package com.back.mozu.global.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

/**
 * Redis 원자적 명령어 래퍼 클래스
 *
 * 역할 분담:
 *   - 이 클래스(RedisUtil): Redis 명령어를 얇게 감싸는 인프라 계층. 비즈니스 로직 없음.
 *   - LockService:          lockAcquire/lockRelease 를 조합해 분산 락 비즈니스 처리
 *   - QueueService: zAdd/zRank/zPopMin 등을 조합해 대기열 비즈니스 처리
 *
 * Redis 키 네이밍 컨벤션:
 *   대기열: "queue:{timeSlotId}"          → Sorted Set, score = 진입 시각(ms)
 *   분산락: "lock:{timeSlotId}"           → String, value = 락 토큰(UUID)
 *   복구용: "waiting:{userId}"            → String, value = reservationId
 */
@Component
@RequiredArgsConstructor
public class RedisUtil {

    private final RedisTemplate<String, String> redisTemplate;

    // =========================================================
    // 대기열 (Sorted Set)
    // =========================================================
    // score가 낮을수록 앞 순번 → score에 System.currentTimeMillis() 사용
    // 같은 ms에 들어와도 UUID로 동일 score 충돌 없음

    /**
     * 대기열에 유저 추가
     * 내부적으로 ZADD 명령어 사용 (원자적)
     *
     * @param key   대기열 키 ("queue:{timeSlotId}")
     * @param value 유저 식별자 (userId 문자열)
     * @param score 진입 시각 (System.currentTimeMillis())
     */
    public void zAdd(String key, String value, double score) {
        redisTemplate.opsForZSet().add(key, value, score);
    }

    /**
     * 현재 대기 순번 조회 (0부터 시작)
     * null 반환 시 대기열에 없는 것
     *
     * @param key   대기열 키
     * @param value 유저 식별자
     * @return 0-based 순번, 없으면 null
     */
    public Long zRank(String key, String value) {
        return redisTemplate.opsForZSet().rank(key, value);
    }

    /**
     * 대기열 맨 앞 유저를 꺼내고 삭제 (ZPOPMIN, 원자적)
     * QueueService에서 처리 순서를 뽑을 때 사용
     *
     * @param key 대기열 키
     * @return 가장 앞 유저의 value, 대기열 비어있으면 null
     */
    public String zPopMin(String key) {
        Set<ZSetOperations.TypedTuple<String>> result =
                redisTemplate.opsForZSet().popMin(key, 1);

        if (result == null || result.isEmpty()) return null;
        return result.iterator().next().getValue();
    }

    /**
     * 대기열 전체 크기 조회
     *
     * @param key 대기열 키
     * @return 대기 인원 수
     */
    public Long zSize(String key) {
        return redisTemplate.opsForZSet().size(key);
    }

    /**
     * 대기열에서 특정 유저 제거
     * 예약 취소, 타임아웃 등으로 대기열 이탈할 때 사용
     *
     * @param key   대기열 키
     * @param value 유저 식별자
     */
    public void zRemove(String key, String value) {
        redisTemplate.opsForZSet().remove(key, value);
    }

    // =========================================================
    // 분산 락 (SET NX PX)
    // =========================================================
    // SET key value NX PX ttl
    //   NX: key가 없을 때만 저장 → 이미 락 잡힌 상태면 실패
    //   PX: TTL(ms) 설정 → 서버 죽어도 자동 해제 (데드락 방지)
    //
    // value에 UUID 토큰을 저장하는 이유:
    //   락 해제 시 "내가 잡은 락인지" 검증하기 위해
    //   토큰 없이 그냥 DEL 하면 다른 서버가 잡은 락을 내가 해제하는 문제 발생

    /**
     * 분산 락 획득 시도 (원자적 SET NX PX)
     *
     * @param lockKey   락 키 ("lock:{timeSlotId}")
     * @param token     락 소유자 식별 토큰 (UUID.randomUUID().toString())
     * @param ttlMillis 락 만료 시간 (ms), 권장값: 3000~5000ms
     * @return true: 락 획득 성공, false: 이미 다른 곳에서 락 보유 중
     */
    public boolean lockAcquire(String lockKey, String token, long ttlMillis) {
        Boolean result = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, token, Duration.ofMillis(ttlMillis));
        return Boolean.TRUE.equals(result);
    }

    /**
     * 분산 락 해제
     * 반드시 토큰 검증 후 삭제 → 내가 잡은 락만 해제
     * get → compare → delete 를 Lua 스크립트로 원자적 처리
     *
     * [왜 Lua인가]
     * get 후 delete 사이에 TTL 만료 + 다른 서버가 락 재획득 하면
     * 남의 락을 지우는 문제 발생 → Lua로 묶으면 원자적으로 실행됨
     *
     * @param lockKey 락 키
     * @param token   락 획득 시 사용했던 토큰
     */
    public void lockRelease(String lockKey, String token) {
        String luaScript =
                "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                        "    return redis.call('del', KEYS[1]) " +
                        "else " +
                        "    return 0 " +
                        "end";

        redisTemplate.execute(
                new org.springframework.data.redis.core.script.DefaultRedisScript<>(luaScript, Long.class),
                java.util.List.of(lockKey),
                token
        );
    }

    // =========================================================
    // 재접속 복구용 (String)
    // =========================================================
    // 브라우저 종료 후 재접속 시 유저의 진행 중인 reservationId 복구
    // WaitingRoomService에서 사용

    /**
     * 재접속 복구용 데이터 저장
     * 유저가 예약 시도 후 브라우저를 닫아도 reservationId 복구 가능
     *
     * @param key   복구 키 ("waiting:{userId}")
     * @param value reservationId 문자열
     * @param ttl   유지 시간 (예: Duration.ofMinutes(10))
     */
    public void set(String key, String value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl);
    }

    /**
     * 키에 해당하는 값 조회
     *
     * @param key 조회할 키
     * @return 저장된 value, 없으면 null
     */
    public String get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    /**
     * 키 삭제
     * 예약 확정/취소 완료 후 복구 데이터 정리할 때 사용
     *
     * @param key 삭제할 키
     */
    public void delete(String key) {
        redisTemplate.delete(key);
    }

    // =========================================================
    // 키 네이밍 헬퍼 (static)
    // =========================================================
    // 키 문자열을 여기서 중앙 관리 → LockService, QueueService에서 import해서 사용

    /** 대기열 키 생성 */
    public static String queueKey(String timeSlotId) {
        return "queue:" + timeSlotId;
    }

    /** 분산 락 키 생성 */
    public static String lockKey(String timeSlotId) {
        return "lock:" + timeSlotId;
    }

    /** 재접속 복구 키 생성 */
    public static String waitingKey(String userId) {
        return "waiting:" + userId;
    }

    /** 락 토큰 생성 */
    public static String generateToken() {
        return UUID.randomUUID().toString();
    }
}