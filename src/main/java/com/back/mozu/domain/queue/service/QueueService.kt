package com.back.mozu.domain.queue.service

import com.back.mozu.domain.customer.service.CustomerService
import com.back.mozu.domain.queue.dto.QueueDto.AttemptRequest
import com.back.mozu.domain.queue.dto.QueueDto.AttemptResponse
import com.back.mozu.domain.queue.dto.QueueDto.StatusResponse
import com.back.mozu.domain.reservation.entity.Reservation
import com.back.mozu.domain.reservation.entity.ReservationStatus
import com.back.mozu.domain.reservation.repository.ReservationRepository
import com.back.mozu.domain.reservation.repository.TimeSlotRepository
import com.back.mozu.domain.reservation.service.ReservationAsyncProcessor
import com.back.mozu.global.redis.RedisUtil
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

@Service
class QueueService(
    private val reservationRepository: ReservationRepository,
    private val timeSlotRepository: TimeSlotRepository,
    private val asyncProcessor: ReservationAsyncProcessor,
    private val customerService: CustomerService,
    private val redisUtil: RedisUtil,
    private val redisTemplate: RedisTemplate<String, String>,
) {
    @Transactional
    fun enqueueAttempt(userId: UUID, request: AttemptRequest): AttemptResponse {
        val customer = customerService.findById(userId)
            ?: throw IllegalArgumentException("존재하지 않는 사용자입니다.")

        require(!customer.isPenaltyActive(LocalDateTime.now())) {
            "현재 예약이 제한된 사용자입니다."
        }

        require(request.guestCount >= 1) {
            "예약 인원은 1명 이상이어야 합니다."
        }

        val timeSlot = timeSlotRepository.findByDateAndTime(request.date, request.time)
            ?: throw IllegalArgumentException("존재하지 않는 시간대입니다.")

        val timeSlotId = requireNotNull(timeSlot.id) {
            "타임슬롯 ID가 생성되지 않았습니다."
        }

        try {
            val existingRank = redisUtil.zRank(
                RedisUtil.queueKey(timeSlotId.toString()),
                userId.toString(),
            )

            require(existingRank == null) {
                "이미 대기열에 있습니다."
            }
        } catch (e: DataAccessException) {
            log.warn("Redis 연결 실패 - DB에서 중복 진입 체크로 전환: {}", e.message)
        }

        val isDuplicate = reservationRepository.existsByUserIdAndTimeSlotAndStatusNot(
            userId,
            timeSlot,
            ReservationStatus.CANCELED,
        )

        require(!isDuplicate) {
            "이미 처리 중이거나 완료된 예약이 있습니다."
        }

        val reservation = Reservation(
            userId = userId,
            timeSlot = timeSlot,
            guestCount = request.guestCount,
            status = ReservationStatus.PENDING,
        )

        val savedReservation = reservationRepository.save(reservation)
        val reservationId = requireNotNull(savedReservation.id) {
            "예약 ID가 생성되지 않았습니다."
        }

        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCommit() {
                    addToRedisQueue(
                        RedisUtil.queueKey(timeSlotId.toString()),
                        userId.toString(),
                        reservationId,
                    )

                    asyncProcessor.processReservation(
                        reservationId,
                        timeSlotId,
                        request.guestCount,
                    )
                }
            },
        )

        return AttemptResponse(reservationId)
    }

    @Transactional(readOnly = true)
    fun getAttemptStatus(attemptId: UUID): StatusResponse {
        val reservation = reservationRepository.findById(attemptId)
            .orElseThrow { IllegalArgumentException("존재하지 않는 예약 시도입니다.") }

        val status = requireNotNull(reservation.status) {
            "예약 상태가 존재하지 않습니다."
        }

        if (status != ReservationStatus.PENDING) {
            return StatusResponse(status, null, null)
        }

        val timeSlot = requireNotNull(reservation.timeSlot) {
            "예약의 타임슬롯이 존재하지 않습니다."
        }

        val timeSlotId = requireNotNull(timeSlot.id) {
            "타임슬롯 ID가 생성되지 않았습니다."
        }

        val reservationUserId = requireNotNull(reservation.userId) {
            "예약 사용자 ID가 존재하지 않습니다."
        }

        val queueKey = RedisUtil.queueKey(timeSlotId.toString())
        val userIdStr = reservationUserId.toString()

        return try {
            var rank = redisUtil.zRank(queueKey, userIdStr)

            if (rank == null) {
                log.warn("Redis 대기열에 유저 없음 - DB 기준 복구 시도: userId={}", userIdStr)
                recoverQueueFromDB(timeSlotId)
                rank = redisUtil.zRank(queueKey, userIdStr)
            }

            val displayRank = rank?.plus(1)
            val estimatedWaitMinutes = rank?.let {
                (it * AVG_PROCESS_SECONDS) / 60
            }

            StatusResponse(status, displayRank, estimatedWaitMinutes)
        } catch (e: DataAccessException) {
            log.error("Redis 연결 실패 - 순번 없이 상태만 반환: {}", e.message)
            StatusResponse(status, null, null)
        }
    }

    fun recoverQueueFromDB(timeSlotId: UUID) {
        log.info("Redis 대기열 복구 시작: timeSlotId={}", timeSlotId)

        val pendings = reservationRepository
            .findByTimeSlotIdAndStatusOrderByCreatedAt(timeSlotId, ReservationStatus.PENDING)

        if (pendings.isEmpty()) {
            log.info("복구할 대기열 없음: timeSlotId={}", timeSlotId)
            return
        }

        val queueKey = RedisUtil.queueKey(timeSlotId.toString())

        redisTemplate.delete(queueKey)

        pendings.forEachIndexed { index, reservation ->
            val reservationUserId = requireNotNull(reservation.userId) {
                "예약 사용자 ID가 존재하지 않습니다."
            }

            val reservationId = requireNotNull(reservation.id) {
                "예약 ID가 생성되지 않았습니다."
            }

            redisUtil.zAdd(queueKey, reservationUserId.toString(), index.toDouble())
            redisUtil.set(
                RedisUtil.waitingKey(reservationUserId.toString()),
                reservationId.toString(),
                QUEUE_TTL,
            )
        }

        log.info("Redis 대기열 복구 완료: {}건", pendings.size)
    }

    private fun addToRedisQueue(queueKey: String, userIdStr: String, reservationId: UUID) {
        try {
            redisUtil.zAdd(queueKey, userIdStr, System.currentTimeMillis().toDouble())

            redisUtil.set(
                RedisUtil.waitingKey(userIdStr),
                reservationId.toString(),
                QUEUE_TTL,
            )
        } catch (e: DataAccessException) {
            log.error("Redis 대기열 추가 실패 (DB에는 저장됨): userId={}, error={}", userIdStr, e.message)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(QueueService::class.java)

        private const val AVG_PROCESS_SECONDS = 3L

        private val QUEUE_TTL: Duration = Duration.ofMinutes(10)
    }
}