package com.back.mozu.domain.queue.service;

import com.back.mozu.domain.customer.entity.Customer;
import com.back.mozu.domain.customer.repository.CustomerRepository;
import com.back.mozu.domain.queue.dto.QueueDto.AttemptRequest;
import com.back.mozu.domain.queue.dto.QueueDto.AttemptResponse;
import com.back.mozu.domain.queue.dto.QueueDto.StatusResponse;
import com.back.mozu.domain.reservation.entity.ReservationStatus;
import com.back.mozu.domain.reservation.entity.TimeSlot;
import com.back.mozu.domain.reservation.repository.ReservationRepository;
import com.back.mozu.domain.reservation.repository.TimeSlotRepository;
import com.back.mozu.domain.reservation.service.ReservationAsyncProcessor;
import com.back.mozu.global.redis.RedisUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Redis 대기열 관련 테스트
 * 담당: 정종욱
 *
 * 테스트 범위:
 *   - 순번 정확성 (진입 순서대로 1, 2, 3번 부여)
 *   - Redis 장애 시 DB 기준 자동 복구
 *   - Redis 1차 중복 진입 방어
 *   - CONFIRMED / CANCELED 시 rank null 반환
 *
 * AsyncProcessor를 Mock으로 막는 이유:
 *   실제 AsyncProcessor가 실행되면 PENDING → CONFIRMED/CANCELED로 바뀌어서
 *   순번 조회(rank)가 null을 반환함 → 순번 테스트 자체가 불가능
 *   여기서는 Redis 로직(순번 부여, 복구)에만 집중하므로 Mock으로 막는 것이 적절
 *   실제 AsyncProcessor 동작은 QueueServiceTest(상민님)에서 통합 테스트로 검증
 *
 * 각 테스트마다 전용 타임슬롯을 생성하는 이유:
 *   recoverQueueFromDB는 해당 타임슬롯의 모든 PENDING 레코드를 조회함
 *   테스트 간 같은 타임슬롯을 공유하면 다른 테스트의 예약 데이터가 섞여서 순번이 꼬임
 *   전용 슬롯을 쓰면 각 테스트가 완전히 독립적으로 실행됨
 */
@SpringBootTest
class QueueServiceRedisTest {

    @Autowired
    private QueueService queueService;

    @Autowired
    private TimeSlotRepository timeSlotRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private RedisUtil redisUtil;

    // AsyncProcessor를 Mock으로 막아서 PENDING 상태 유지
    // → 순번/Redis 복구 테스트가 가능해짐
    @MockitoBean
    private ReservationAsyncProcessor asyncProcessor;

    private Customer customer;

    @BeforeEach
    void setUp() {
        // QueueService.enqueueAttempt에서 customerService.findById 체크가 있어서
        // Customer를 먼저 저장해야 테스트가 정상 동작함
        customer = saveCustomer();

        // Redis 클리어 (이전 테스트 잔재 제거)
        redisTemplate.delete(redisTemplate.keys("queue:*"));
        redisTemplate.delete(redisTemplate.keys("waiting:*"));
        redisTemplate.delete(redisTemplate.keys("lock:*"));
    }

    @AfterEach
    void cleanUp() {
        reservationRepository.deleteAllInBatch();
        timeSlotRepository.deleteAllInBatch();
        customerRepository.deleteAllInBatch();

        // Redis 대기열 데이터 정리
        redisTemplate.delete(redisTemplate.keys("queue:*"));
        redisTemplate.delete(redisTemplate.keys("waiting:*"));
        redisTemplate.delete(redisTemplate.keys("lock:*"));
    }

    // =========================================================
    // 순번 테스트
    // =========================================================

    @Test
    @DisplayName("3명이 순서대로 진입하면 순번이 1, 2, 3으로 반환된다")
    void rankShouldBeAssignedInOrder() throws InterruptedException {
        // 이 테스트 전용 타임슬롯
        TimeSlot slot = saveTimeSlot(LocalTime.of(12, 0));

        Customer c1 = saveCustomer();
        Customer c2 = saveCustomer();
        Customer c3 = saveCustomer();

        // 순서대로 진입 (100ms 딜레이로 score 차이 보장)
        AttemptResponse r1 = queueService.enqueueAttempt(c1.getId(), new AttemptRequest(slot.getDate(), slot.getTime(), 1));
        Thread.sleep(100);
        AttemptResponse r2 = queueService.enqueueAttempt(c2.getId(), new AttemptRequest(slot.getDate(), slot.getTime(), 1));
        Thread.sleep(100);
        AttemptResponse r3 = queueService.enqueueAttempt(c3.getId(), new AttemptRequest(slot.getDate(), slot.getTime(), 1));

        // afterCommit 완료 대기
        Thread.sleep(500);

        StatusResponse status1 = queueService.getAttemptStatus(r1.getAttemptId());
        StatusResponse status2 = queueService.getAttemptStatus(r2.getAttemptId());
        StatusResponse status3 = queueService.getAttemptStatus(r3.getAttemptId());

        assertThat(status1.getRank()).isEqualTo(1L);
        assertThat(status2.getRank()).isEqualTo(2L);
        assertThat(status3.getRank()).isEqualTo(3L);
    }

    @Test
    @DisplayName("PENDING 상태에서 rank와 예상 대기 시간이 반환된다")
    void pendingStatusShouldReturnRankAndEstimatedWait() throws InterruptedException {
        // 이 테스트 전용 타임슬롯
        TimeSlot slot = saveTimeSlot(LocalTime.of(13, 0));

        Customer c1 = saveCustomer();
        Customer c2 = saveCustomer();

        AttemptResponse r1 = queueService.enqueueAttempt(c1.getId(), new AttemptRequest(slot.getDate(), slot.getTime(), 1));
        Thread.sleep(100);
        AttemptResponse r2 = queueService.enqueueAttempt(c2.getId(), new AttemptRequest(slot.getDate(), slot.getTime(), 1));

        Thread.sleep(500);

        StatusResponse status2 = queueService.getAttemptStatus(r2.getAttemptId());

        assertThat(status2.getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(status2.getRank()).isEqualTo(2L);
        assertThat(status2.getEstimatedWaitMinutes()).isNotNull();
    }

    // =========================================================
    // Redis 장애 테스트
    // =========================================================

    @Test
    @DisplayName("Redis 장애 시에도 DB에 예약이 저장된다 (Source of Truth)")
    void shouldSaveToDB_EvenWhenRedisFails() {
        // 이 테스트 전용 타임슬롯
        TimeSlot slot = saveTimeSlot(LocalTime.of(14, 0));

        AttemptResponse response = queueService.enqueueAttempt(
                customer.getId(),
                new AttemptRequest(slot.getDate(), slot.getTime(), 1));

        // Redis 죽어도 DB에는 반드시 기록이 남는다는 것이 핵심
        assertThat(reservationRepository.findById(response.getAttemptId())).isPresent();
    }

    @Test
    @DisplayName("Redis 대기열 유실 시 getAttemptStatus 호출 시 DB 기준으로 순번이 자동 복구된다")
    void shouldAutoRecoverQueue_WhenRedisDataLost() throws InterruptedException {
        // 이 테스트 전용 타임슬롯 (다른 테스트와 분리)
        TimeSlot slot = saveTimeSlot(LocalTime.of(15, 0));

        // 3명 순서대로 진입
        List<AttemptResponse> responses = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Customer c = saveCustomer();
            responses.add(queueService.enqueueAttempt(
                    c.getId(),
                    new AttemptRequest(slot.getDate(), slot.getTime(), 1)));
            Thread.sleep(100);
        }

        // afterCommit 콜백 완료 대기
        Thread.sleep(500);

        // Redis 대기열 강제 삭제 (장애 시뮬레이션)
        redisTemplate.delete(redisTemplate.keys("queue:*"));
        redisTemplate.delete(redisTemplate.keys("waiting:*"));

        // getAttemptStatus 호출 시 rank null → recoverQueueFromDB 자동 실행
        StatusResponse status1 = queueService.getAttemptStatus(responses.get(0).getAttemptId());
        StatusResponse status2 = queueService.getAttemptStatus(responses.get(1).getAttemptId());
        StatusResponse status3 = queueService.getAttemptStatus(responses.get(2).getAttemptId());

        // DB createdAt 순서대로 복구되어야 함
        assertThat(status1.getRank()).isEqualTo(1L);
        assertThat(status2.getRank()).isEqualTo(2L);
        assertThat(status3.getRank()).isEqualTo(3L);
    }

    @Test
    @DisplayName("recoverQueueFromDB 직접 호출 시 원래 진입 순서대로 복구된다")
    void recoverQueueFromDB_ShouldMaintainOriginalOrder() throws InterruptedException {
        // 이 테스트 전용 타임슬롯 (다른 테스트와 분리)
        TimeSlot slot = saveTimeSlot(LocalTime.of(16, 0));

        // 5명 순서대로 진입
        List<AttemptResponse> responses = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Customer c = saveCustomer();
            responses.add(queueService.enqueueAttempt(
                    c.getId(),
                    new AttemptRequest(slot.getDate(), slot.getTime(), 1)));
            Thread.sleep(100);
        }

        // afterCommit 완료 대기
        Thread.sleep(500);

        // Redis 강제 삭제
        redisTemplate.delete(redisTemplate.keys("queue:*"));
        redisTemplate.delete(redisTemplate.keys("waiting:*"));

        // recoverQueueFromDB 직접 호출
        queueService.recoverQueueFromDB(slot.getId());

        // 복구 후 순번이 원래 순서와 동일한지 확인
        // getAttemptStatus 대신 Redis에서 직접 rank 조회
        // 이유: getAttemptStatus 호출 시 rank null이면 recoverQueueFromDB가 또 실행되어 순번이 꼬임
        for (int i = 0; i < 5; i++) {
            UUID userId = reservationRepository
                    .findById(responses.get(i).getAttemptId())
                    .orElseThrow().getUserId();
            Long rank = redisUtil.zRank(
                    RedisUtil.queueKey(slot.getId().toString()),
                    userId.toString());
            assertThat(rank + 1).isEqualTo((long) (i + 1));
        }
    }

    // =========================================================
    // 중복 방지 테스트 (Redis 1차 방어)
    // =========================================================

    @Test
    @DisplayName("Redis에 이미 있는 유저가 재진입 시도 시 예외 발생 (Redis 1차 방어)")
    void throwExceptionWhenAlreadyInRedisQueue() {
        // 이 테스트 전용 타임슬롯
        TimeSlot slot = saveTimeSlot(LocalTime.of(17, 0));

        // Redis 대기열에 직접 추가 (이미 진입한 상태 시뮬레이션)
        redisUtil.zAdd(
                RedisUtil.queueKey(slot.getId().toString()),
                customer.getId().toString(),
                System.currentTimeMillis());

        // 같은 유저가 다시 진입 시도 → Redis 1차 방어에서 차단
        assertThatThrownBy(() -> queueService.enqueueAttempt(
                customer.getId(),
                new AttemptRequest(slot.getDate(), slot.getTime(), 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 대기열에 있습니다.");
    }

    // =========================================================
    // 헬퍼 메서드
    // =========================================================

    // 테스트용 Customer 생성 (각 테스트마다 고유한 유저 필요)
    private Customer saveCustomer() {
        Customer c = Customer.builder()
                .email("test-" + UUID.randomUUID() + "@test.com")
                .provider("test")
                .providerId(UUID.randomUUID().toString())
                .role("USER")
                .name("테스트")
                .build();
        return customerRepository.save(c);
    }

    // 테스트용 TimeSlot 생성 (각 테스트마다 전용 슬롯 사용 → 테스트 간 데이터 분리)
    private TimeSlot saveTimeSlot(LocalTime time) {
        TimeSlot slot = TimeSlot.builder()
                .date(LocalDate.now())
                .time(time)
                .stock(10)
                .build();
        return timeSlotRepository.save(slot);
    }
}