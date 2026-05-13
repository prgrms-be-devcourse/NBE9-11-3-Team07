package com.back.mozu.domain.queue.service;

import com.back.mozu.domain.queue.dto.QueueDto.AttemptRequest;
import com.back.mozu.domain.queue.dto.QueueDto.AttemptResponse;
import com.back.mozu.domain.queue.dto.QueueDto.StatusResponse;
import com.back.mozu.domain.reservation.entity.Reservation;
import com.back.mozu.domain.reservation.entity.ReservationStatus;
import com.back.mozu.domain.reservation.entity.TimeSlot;
import com.back.mozu.domain.reservation.repository.ReservationRepository;
import com.back.mozu.domain.reservation.repository.TimeSlotRepository;
import com.back.mozu.domain.reservation.service.ReservationAsyncProcessor;
import com.back.mozu.global.redis.RedisUtil;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.back.mozu.domain.customer.entity.Customer;
import com.back.mozu.domain.customer.service.CustomerService;
import java.time.LocalDateTime;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

// 메인 서비스
@Slf4j
@Service
@RequiredArgsConstructor
public class QueueService {

    private final ReservationRepository reservationRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final ReservationAsyncProcessor asyncProcessor;
    private final CustomerService customerService;
    private final RedisUtil redisUtil;
    private final RedisTemplate<String, String> redisTemplate;

    // 1건당 평균 처리 시간 (초) - 예상 대기 시간 계산에 사용
    private static final long AVG_PROCESS_SECONDS = 3L;

    // 대기열 TTL - 10분 안에 처리 안 되면 자동 만료
    private static final Duration QUEUE_TTL = Duration.ofMinutes(10);

    // 예약을 데이터베이스에 저장하고 비동기 처리
    @Transactional
    public AttemptResponse enqueueAttempt(UUID userId, AttemptRequest request) {

        Customer customer = customerService.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        if (customer.isPenaltyActive(LocalDateTime.now())) {
            throw new IllegalArgumentException("현재 예약이 제한된 사용자입니다.");
        }

        // 예약 인원 검증
        if (request.getGuestCount() < 1) {
            throw new IllegalArgumentException("예약 인원은 1명 이상이어야 합니다.");
        }

        TimeSlot timeSlot = timeSlotRepository.findByDateAndTime(request.getDate(), request.getTime())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 시간대입니다."));

        // Redis 중복 진입 체크 (1차 방어) - Redis 살아있으면 DB 조회 전에 먼저 차단
        try {
            Long existingRank = redisUtil.zRank(
                    RedisUtil.queueKey(timeSlot.getId().toString()), userId.toString());
            if (existingRank != null) {
                throw new IllegalArgumentException("이미 대기열에 있습니다.");
            }
        } catch (DataAccessException e) {  // DataAccessException: Redis 연결 실패 포함한 데이터 접근 예외의 상위 클래스
            // Redis 장애 시 DB 체크로 fallback
            log.warn("Redis 연결 실패 - DB에서 중복 진입 체크로 전환: {}", e.getMessage());
        }

        // CANCELED 상태가 아닌 기존 예약 존재 여부 검사 - Redis 장애 시에도 이 체크가 최종 방어선 역할을 함
        boolean isDuplicate = reservationRepository.existsByUserIdAndTimeSlotAndStatusNot(
                userId, timeSlot, ReservationStatus.CANCELED
        );

        if (isDuplicate) {
            throw new IllegalArgumentException("이미 처리 중이거나 완료된 예약이 있습니다.");
        }

        // 객체 생성
        Reservation reservation = Reservation.builder()
                .userId(userId)
                .timeSlot(timeSlot)
                .guestCount(request.getGuestCount())
                .status(ReservationStatus.PENDING)
                .build();

        // DB에 먼저 저장 - Redis보다 DB를 먼저 저장해야 Redis 장애 시에도 데이터 유실이 없음
        reservationRepository.save(reservation);

        // 비동기 작업을 데이터베이스 저장이 끝난 이후에 시작
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // Redis 대기열 추가 + TTL 설정 (DB 커밋 이후 실행 → 싱크 불일치 방지)
                addToRedisQueue(
                        RedisUtil.queueKey(timeSlot.getId().toString()),
                        userId.toString(),
                        reservation.getId()
                );
                asyncProcessor.processReservation(reservation.getId(), timeSlot.getId(), request.getGuestCount());
            }
        });

        return new AttemptResponse(reservation.getId());
    }

    // 현재 상태 응답 - 내 순번이 몇번인지 폴링
    @Transactional
    public StatusResponse getAttemptStatus(UUID attemptId) {
        Reservation reservation = reservationRepository.findById(attemptId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 예약 시도입니다."));

        // CONFIRMED / CANCELED 이면 대기열 순번 필요 없음
        if (reservation.getStatus() != ReservationStatus.PENDING) {
            return new StatusResponse(reservation.getStatus(), null, null);
        }

        // Redis에서 현재 순번 조회
        String queueKey = RedisUtil.queueKey(reservation.getTimeSlot().getId().toString());
        String userIdStr = reservation.getUserId().toString();

        try {
            Long rank = redisUtil.zRank(queueKey, userIdStr);

            if (rank == null) {
                // Redis에 없으면 장애로 유실된 것 → DB 기준으로 복구 후 재조회
                log.warn("Redis 대기열에 유저 없음 - DB 기준 복구 시도: userId={}", userIdStr);
                recoverQueueFromDB(reservation.getTimeSlot().getId());
                rank = redisUtil.zRank(queueKey, userIdStr);
            }

            // rank는 0-based → 유저에게 보여줄 땐 1-based로 변환 (이유 : Redis zRank가 0-based로 반환하는 건 Redis의 기본 동작)
            // ex) rank=0 → 1번째, rank=4 → 5번째
            Long displayRank = (rank != null) ? rank + 1 : null;

            // 예상 대기 시간 = 내 앞 순번 수 * 1건당 평균 처리 시간(초) / 60
            // ex) rank=9 (10번째) → 9 * 3초 / 60 = 0분 (1분 미만)
            Long estimatedWaitMinutes = (rank != null) ? (rank * AVG_PROCESS_SECONDS) / 60 : null;

            return new StatusResponse(reservation.getStatus(), displayRank, estimatedWaitMinutes);

        } catch (DataAccessException e) {
            // Redis 완전 장애 시 순번 없이 상태만 반환
            log.error("Redis 연결 실패 - 순번 없이 상태만 반환: {}", e.getMessage());
            return new StatusResponse(reservation.getStatus(), null, null);
        }
    }

    // Redis 장애 시 DB 기준으로 대기열 복구 (Source of Truth = DB)
    // DB의 PENDING 레코드를 createdAt 순으로 읽어서 Redis Sorted Set 재구성
    public void recoverQueueFromDB(UUID timeSlotId) {
        log.info("Redis 대기열 복구 시작: timeSlotId={}", timeSlotId);

        List<Reservation> pendings = reservationRepository
                .findByTimeSlotIdAndStatusOrderByCreatedAt(timeSlotId, ReservationStatus.PENDING);

        if (pendings.isEmpty()) {
            log.info("복구할 대기열 없음: timeSlotId={}", timeSlotId);
            return;
        }

        String queueKey = RedisUtil.queueKey(timeSlotId.toString());

        // 기존 키 완전 삭제 후 재구성 (완전 복구 방식)
        // 이유: recoverQueueFromDB는 여러 유저가 동시에 폴링하면 여러 번 호출될 수 있음
        //       부분 복구(zAdd만)는 호출마다 기존 데이터의 score가 업데이트되어 순번이 바뀜 → 멱등성 X
        //       완전 삭제 후 재구성하면 몇 번 호출돼도 항상 DB 기준으로 동일한 결과 → 멱등성 O
        //       DB가 Source of Truth이므로 Redis 기존 데이터를 신뢰하지 않는 것이 원칙에도 맞음
        redisTemplate.delete(queueKey);

        for (int i = 0; i < pendings.size(); i++) {
            Reservation r = pendings.get(i);
            // score = i (0, 1, 2, ...) 로 순번 부여
            // createdAt을 score로 쓰면 MySQL DATETIME이 초 단위라 같은 초에 생성된 레코드는 score가 동일해짐
            // → Redis가 UUID 사전순으로 정렬 → 원래 순서 보장 안 됨
            // i를 score로 쓰면 DB ORDER BY createdAt 순서가 score에 그대로 반영되어 순번 보장
            redisUtil.zAdd(queueKey, r.getUserId().toString(), i);
            redisUtil.set(RedisUtil.waitingKey(r.getUserId().toString()), r.getId().toString(), QUEUE_TTL);
        }

        log.info("Redis 대기열 복구 완료: {}건", pendings.size());
    }

    // Redis 대기열 추가 + TTL 설정
    private void addToRedisQueue(String queueKey, String userIdStr, UUID reservationId) {
        try {
            // Sorted Set에 추가 (score = 현재 시각 ms → 진입 순서 보장)
            redisUtil.zAdd(queueKey, userIdStr, System.currentTimeMillis());

            // 복구용 데이터 저장 (브라우저 꺼도 순번 유지)
            redisUtil.set(RedisUtil.waitingKey(userIdStr), reservationId.toString(), QUEUE_TTL);

        } catch (DataAccessException e) {
            // Redis 장애 시 로그만 남김 → DB가 Source of Truth이므로 데이터 유실 없음
            // 다음 폴링 시 getAttemptStatus → recoverQueueFromDB가 자동 복구
            log.error("Redis 대기열 추가 실패 (DB에는 저장됨): userId={}, error={}", userIdStr, e.getMessage());
        }
    }
}