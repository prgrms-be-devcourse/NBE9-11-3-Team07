package com.back.mozu.domain.queue.service

import com.back.mozu.domain.customer.entity.Customer
import com.back.mozu.domain.customer.repository.CustomerRepository
import com.back.mozu.domain.queue.dto.QueueDto.AttemptRequest
import com.back.mozu.domain.reservation.entity.ReservationStatus
import com.back.mozu.domain.reservation.entity.TimeSlot
import com.back.mozu.domain.reservation.repository.ReservationRepository
import com.back.mozu.domain.reservation.repository.TimeSlotRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.RedisTemplate
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

@SpringBootTest
class QueueServiceTest @Autowired constructor(
    private val queueService: QueueService,
    private val timeSlotRepository: TimeSlotRepository,
    private val reservationRepository: ReservationRepository,
    private val customerRepository: CustomerRepository,
    private val redisTemplate: RedisTemplate<String, String>,
) {

    @BeforeEach
    fun setUp() {
        cleanUp()
    }

    @AfterEach
    fun tearDown() {
        cleanUp()
    }

    private fun cleanUp() {
        reservationRepository.deleteAllInBatch()
        timeSlotRepository.deleteAllInBatch()
        customerRepository.deleteAllInBatch()

        redisTemplate.keys("queue:*")?.let { redisTemplate.delete(it) }
        redisTemplate.keys("waiting:*")?.let { redisTemplate.delete(it) }
        redisTemplate.keys("lock:*")?.let { redisTemplate.delete(it) }
    }

    private fun createAndSaveCustomer(email: String, providerId: String): Customer {
        val customer = Customer(
            email = email,
            provider = "local",
            providerId = providerId,
            role = "USER",
            password = null,
            name = "테스트",
        )

        return customerRepository.save(customer)
    }

    private fun createAndSaveTimeSlot(
        stock: Int,
        date: LocalDate = LocalDate.now(),
        time: LocalTime = LocalTime.of(12, 0),
    ): TimeSlot {
        val timeSlot = TimeSlot(
            date = date,
            time = time,
            stock = stock,
        )

        return timeSlotRepository.save(timeSlot)
    }

    private fun waitUntilProcessed(expectedCount: Int, timeoutMillis: Long = 5_000) {
        val deadline = System.currentTimeMillis() + timeoutMillis

        while (System.currentTimeMillis() < deadline) {
            val processedCount = reservationRepository.findAll()
                .count {
                    it.status == ReservationStatus.CONFIRMED ||
                            it.status == ReservationStatus.CANCELED
                }

            if (processedCount >= expectedCount) return

            Thread.sleep(100)
        }
    }

    @Test
    fun `100명이 동시에 10개 남은 좌석을 예약 시 10명만 성공`() {
        // given
        val threadCount = 100
        val executorService = Executors.newFixedThreadPool(32)
        val latch = CountDownLatch(threadCount)

        val timeSlot = createAndSaveTimeSlot(stock = 10)

        val customers = (0 until threadCount).map { i ->
            createAndSaveCustomer("test$i@test.com", "id_$i")
        }

        // when
        customers.forEach { currentCustomer ->
            executorService.execute {
                try {
                    queueService.enqueueAttempt(
                        requireNotNull(currentCustomer.id),
                        AttemptRequest(
                            timeSlot.date,
                            timeSlot.time,
                            1,
                        ),
                    )
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executorService.shutdown()

        waitUntilProcessed(threadCount)

        // then
        val successCount = reservationRepository.findAllByStatus(ReservationStatus.CONFIRMED).size

        assertThat(successCount).isEqualTo(10)
    }

    @Test
    fun `존재하지 않는 타임슬롯으로 예약 시도 시 IllegalArgumentException 발생`() {
        // given
        val customer = createAndSaveCustomer("notfound@test.com", "notfound123")

        val request = AttemptRequest(
            LocalDate.of(2099, 1, 1),
            LocalTime.of(12, 0),
            2,
        )

        // when & then
        assertThatThrownBy {
            queueService.enqueueAttempt(requireNotNull(customer.id), request)
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("존재하지 않는 시간대입니다.")
    }

    @Test
    fun `존재하지 않는 대기 ID로 상태를 조회하면 IllegalArgumentException 발생`() {
        // given
        val fakeAttemptId = UUID.randomUUID()

        // when & then
        assertThatThrownBy {
            queueService.getAttemptStatus(fakeAttemptId)
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("존재하지 않는 예약 시도입니다.")
    }

    @Test
    fun `남은 재고와 예약 인원 동일 시 성공 및 재고 값 0을 반환`() {
        // given
        val customer = createAndSaveCustomer("exact@test.com", "exact123")
        val timeSlot = createAndSaveTimeSlot(stock = 5)

        val request = AttemptRequest(
            timeSlot.date,
            timeSlot.time,
            5,
        )

        // when
        val response = queueService.enqueueAttempt(requireNotNull(customer.id), request)

        waitUntilProcessed(1)

        val status = queueService.getAttemptStatus(response.attemptId)

        // then
        assertThat(status.status).isEqualTo(ReservationStatus.CONFIRMED)

        val updatedSlot = timeSlotRepository.findById(requireNotNull(timeSlot.id)).orElseThrow()
        assertThat(updatedSlot.stock).isEqualTo(0)
    }

    @Test
    fun `남은 재고보다 예약 인원이 많을 시 실패 및 CANCELED 상태 반환`() {
        // given
        val customer = createAndSaveCustomer("exceed@test.com", "exceed123")
        val timeSlot = createAndSaveTimeSlot(stock = 5)

        val request = AttemptRequest(
            timeSlot.date,
            timeSlot.time,
            6,
        )

        // when
        val response = queueService.enqueueAttempt(requireNotNull(customer.id), request)

        waitUntilProcessed(1)

        val status = queueService.getAttemptStatus(response.attemptId)

        // then
        assertThat(status.status).isEqualTo(ReservationStatus.CANCELED)
    }

    @Test
    fun `예약 인원이 1명 미만일 시 IllegalArgumentException 발생`() {
        // given
        val customer = createAndSaveCustomer("invalid@test.com", "invalid123")
        val timeSlot = createAndSaveTimeSlot(stock = 5)

        val request = AttemptRequest(
            timeSlot.date,
            timeSlot.time,
            0,
        )

        // when & then
        assertThatThrownBy {
            queueService.enqueueAttempt(requireNotNull(customer.id), request)
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("예약 인원은 1명 이상이어야 합니다.")
    }

    @Test
    fun `동일한 인원이 같은 시간대에 중복 예약 시도 시 예외 발생`() {
        // given
        val customer = createAndSaveCustomer("dup@test.com", "dup123")
        val timeSlot = createAndSaveTimeSlot(stock = 5)

        val request = AttemptRequest(
            timeSlot.date,
            timeSlot.time,
            2,
        )

        queueService.enqueueAttempt(requireNotNull(customer.id), request)

        redisTemplate.keys("queue:*")?.let { redisTemplate.delete(it) }

        // when & then
        assertThatThrownBy {
            queueService.enqueueAttempt(requireNotNull(customer.id), request)
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("이미 처리 중이거나 완료된 예약이 있습니다.")
    }
}