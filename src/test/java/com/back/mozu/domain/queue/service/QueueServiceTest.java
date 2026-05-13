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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class QueueServiceTest {

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

    @AfterEach
    void cleanUp() {
        reservationRepository.deleteAllInBatch();
        timeSlotRepository.deleteAllInBatch();
        customerRepository.deleteAllInBatch();
        redisTemplate.delete(redisTemplate.keys("queue:*"));
        redisTemplate.delete(redisTemplate.keys("waiting:*"));
        redisTemplate.delete(redisTemplate.keys("lock:*"));
    }

    private Customer createAndSaveCustomer(String email, String providerId) {
        Customer customer = Customer.builder()
                .email(email)
                .provider("local")
                .providerId(providerId)
                .role("USER")
                .name("테스트")
                .build();
        return customerRepository.save(customer);
    }

    @Test
    @DisplayName("100명이 동시에 10개 남은 좌석을 예약 시 10명만 성공")
    void concurrencyTest() throws InterruptedException {
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        TimeSlot timeSlot = TimeSlot.builder()
                .date(LocalDate.now())
                .time(LocalTime.of(12, 0))
                .stock(10)
                .build();
        timeSlotRepository.save(timeSlot);

        List<Customer> customers = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            customers.add(createAndSaveCustomer("test" + i + "@test.com", "id_" + i));
        }

        for (int i = 0; i < threadCount; i++) {
            final Customer currentCustomer = customers.get(i);

            executorService.execute(() -> {
                try {
                    UUID customerId = currentCustomer.getId();
                    queueService.enqueueAttempt(customerId, new AttemptRequest(timeSlot.getDate(), timeSlot.getTime(), 1));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        Thread.sleep(2000);

        int successCount = Math.toIntExact(reservationRepository.findAll().stream()
                .filter(r -> r.getStatus() == ReservationStatus.CONFIRMED).count());
        assertThat(successCount).isEqualTo(10);
    }

    @Test
    @DisplayName("존재하지 않는 타임슬롯으로 예약 시도 시 IllegalArgumentException 발생")
    void throwExceptionWhenTimeSlotNotFound() {
        Customer customer = createAndSaveCustomer("notfound@test.com", "notfound123");
        UUID customerId = customer.getId();

        AttemptRequest request = new AttemptRequest(LocalDate.of(2099, 1, 1), LocalTime.of(12, 0), 2);

        assertThatThrownBy(() -> queueService.enqueueAttempt(customerId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("존재하지 않는 시간대입니다.");
    }

    @Test
    @DisplayName("존재하지 않는 대기 ID로 상태를 조회하면 IllegalArgumentException 발생")
    void throwExceptionWhenAttemptNotFound() {
        UUID fakeAttemptId = UUID.randomUUID();

        assertThatThrownBy(() -> queueService.getAttemptStatus(fakeAttemptId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("존재하지 않는 예약 시도입니다.");
    }

    @Test
    @DisplayName("남은 재고와 예약 인원 동일 시 성공 및 재고 값 0을 반환")
    void successWhenExactStock() throws InterruptedException {
        Customer customer = createAndSaveCustomer("exact@test.com", "exact123");
        UUID customerId = customer.getId();

        TimeSlot timeSlot = TimeSlot.builder()
                .date(LocalDate.now()).time(LocalTime.of(12, 0)).stock(5).build();
        timeSlotRepository.save(timeSlot);

        AttemptRequest request = new AttemptRequest(timeSlot.getDate(), timeSlot.getTime(), 5);
        AttemptResponse response = queueService.enqueueAttempt(customerId, request);
        Thread.sleep(2000);

        StatusResponse status = queueService.getAttemptStatus(response.getAttemptId());
        assertThat(status.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);

        TimeSlot updatedSlot = timeSlotRepository.findById(timeSlot.getId()).orElseThrow();
        assertThat(updatedSlot.getStock()).isEqualTo(0);
    }

    @Test
    @DisplayName("남은 재고보다 예약 인원이 많을 시 실패 및 CANCELED 상태 반환")
    void failWhenRequestExceedsStock() throws InterruptedException {
        Customer customer = createAndSaveCustomer("exceed@test.com", "exceed123");
        UUID customerId = customer.getId();

        TimeSlot timeSlot = TimeSlot.builder()
                .date(LocalDate.now()).time(LocalTime.of(12, 0)).stock(5).build();
        timeSlotRepository.save(timeSlot);

        AttemptRequest request = new AttemptRequest(timeSlot.getDate(), timeSlot.getTime(), 6);
        AttemptResponse response = queueService.enqueueAttempt(customerId, request);
        Thread.sleep(2000);

        StatusResponse status = queueService.getAttemptStatus(response.getAttemptId());
        assertThat(status.getStatus()).isEqualTo(ReservationStatus.CANCELED);
    }

    @Test
    @DisplayName("예약 인원이 1명 미만일 시 IllegalArgumentException 발생")
    void throwExceptionWhenInvalidGuestCount() {
        Customer customer = createAndSaveCustomer("invalid@test.com", "invalid123");
        UUID customerId = customer.getId();

        TimeSlot timeSlot = TimeSlot.builder()
                .date(LocalDate.now()).time(LocalTime.of(12, 0)).stock(5).build();
        timeSlotRepository.save(timeSlot);

        AttemptRequest request = new AttemptRequest(timeSlot.getDate(), timeSlot.getTime(), 0);

        assertThatThrownBy(() -> queueService.enqueueAttempt(customerId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("예약 인원은 1명 이상이어야 합니다.");
    }

    @Test
    @DisplayName("동일한 인원이 같은 시간대에 중복 예약 시도 시 예외 발생")
    void throwExceptionWhenDuplicateRequest() {
        Customer customer = createAndSaveCustomer("dup@test.com", "dup123");
        UUID customerId = customer.getId();

        TimeSlot timeSlot = TimeSlot.builder()
                .date(LocalDate.now()).time(LocalTime.of(12, 0)).stock(5).build();
        timeSlotRepository.save(timeSlot);

        AttemptRequest request = new AttemptRequest(timeSlot.getDate(), timeSlot.getTime(), 2);
        queueService.enqueueAttempt(customerId, request);

        // Redis 클리어 → DB 2차 방어만 테스트
        redisTemplate.delete(redisTemplate.keys("queue:*"));

        assertThatThrownBy(() -> queueService.enqueueAttempt(customerId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 처리 중이거나 완료된 예약이 있습니다.");
    }
}