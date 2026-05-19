package com.back.mozu.domain.reservation.service

import com.back.mozu.domain.queue.service.LockService
import com.back.mozu.domain.reservation.repository.ReservationRepository
import com.back.mozu.domain.reservation.repository.TimeSlotRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.repository.findByIdOrNull
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.util.UUID

@Service
class ReservationAsyncProcessor(
    private val reservationRepository: ReservationRepository,
    private val timeSlotRepository: TimeSlotRepository,
    private val lockService: LockService,
    private val redisTemplate: RedisTemplate<String, String>, // Fail-Fast용 Redis 추가
) {

    @Async
    @Transactional
    fun processReservation(reservationId: UUID, timeSlotId: UUID, guestCount: Int) {
        val reservation = reservationRepository.findByIdOrNull(reservationId)
            ?: throw IllegalArgumentException("예약 기록을 찾을 수 없습니다.")
        val timeSlot = timeSlotRepository.findByIdOrNull(timeSlotId)
            ?: throw IllegalArgumentException("타임슬롯을 찾을 수 없습니다.")

        val lockToken = reservationId.toString()
        var lockAcquired = false
        var stockOccupied = false

        try {
            lockAcquired = lockService.acquireLock(timeSlotId.toString(), lockToken)
            if (!lockAcquired) {
                reservation.cancelReservation("LOCK_ACQUIRE_FAIL")
                return
            }
        val timeSlotIdStr = timeSlotId.toString()
        val occupiedKey = "isOccupied:$timeSlotIdStr"
        val isOccupied = redisTemplate.opsForValue().get(occupiedKey)?.toBoolean() ?: false
        if (isOccupied) {
            cancelReservationSafely(reservationId, "ALREADY_OCCUPIED_FAIL_FAST")
            return
        }

        var lockAcquired = false
        try {
            lockAcquired = lockService.acquireLock(timeSlotIdStr, reservationId.toString())
            if (!lockAcquired) {
                cancelReservationSafely(reservationId, "LOCK_ACQUIRE_FAIL")
                return
            }

            val reservation = reservationRepository.findByIdOrNull(reservationId)
                ?: throw IllegalArgumentException("예약 기록을 찾을 수 없습니다.")
            val timeSlot = timeSlotRepository.findByIdOrNull(timeSlotId)
                ?: throw IllegalArgumentException("타임슬롯을 찾을 수 없습니다.")

            timeSlot.occupy(guestCount)
            stockOccupied = true

            reservation.confirmReservation()
        } catch (e: OptimisticLockingFailureException) {
            reservation.cancelReservation("OPTIMISTIC_LOCK_FAIL")

            if (timeSlot.stock == 0) {
                redisTemplate.opsForValue().set(occupiedKey, "true")
            }

        } catch (e: ObjectOptimisticLockingFailureException) {
            cancelReservationSafely(reservationId, "OPTIMISTIC_LOCK_FAIL")
        } catch (e: IllegalArgumentException) {
            reservation.cancelReservation("RESERVATION_FAILED")
            cancelReservationSafely(reservationId, "RESERVATION_FAILED")
        } catch (e: IllegalStateException) {
            if (stockOccupied) {
                timeSlot.release(guestCount)
            }
            reservation.cancelReservation("RESERVATION_FAILED")
            cancelReservationSafely(reservationId, "RESERVATION_FAILED")
        } catch (e: Exception) {
            log.error(
                "비동기 예약 처리 중 시스템 예외 발생: reservationId={}, timeSlotId={}, guestCount={}",
                reservationId,
                timeSlotId,
                guestCount,
                e,
            )

            if (stockOccupied) {
                timeSlot.release(guestCount)
            }

            reservation.cancelReservation("SYSTEM_ERROR")
            cancelReservationSafely(reservationId, "SYSTEM_ERROR")
        } finally {
            if (lockAcquired) {
                TransactionSynchronizationManager.registerSynchronization(
                    object : TransactionSynchronization {
                        override fun afterCompletion(status: Int) {
                            lockService.releaseLock(timeSlotIdStr, reservationId.toString())
                        }
                    }
                )
            }
        }
    }
    private fun cancelReservationSafely(reservationId: UUID, reason: String) {
        val reservation = reservationRepository.findByIdOrNull(reservationId) ?: return
        reservation.cancelReservation(reason)
    }

    companion object {
        private val log = LoggerFactory.getLogger(ReservationAsyncProcessor::class.java)
    }
}