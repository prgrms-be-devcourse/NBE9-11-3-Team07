package com.back.mozu.domain.reservation.service

import com.back.mozu.domain.queue.service.LockService
import com.back.mozu.domain.reservation.repository.ReservationRepository
import com.back.mozu.domain.reservation.repository.TimeSlotRepository
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.repository.findByIdOrNull
import org.springframework.orm.ObjectOptimisticLockingFailureException
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
            reservation.confirmReservation()

            if (timeSlot.stock == 0) {
                redisTemplate.opsForValue().set(occupiedKey, "true")
            }

        } catch (e: ObjectOptimisticLockingFailureException) {
            cancelReservationSafely(reservationId, "OPTIMISTIC_LOCK_FAIL")
        } catch (e: IllegalArgumentException) {
            cancelReservationSafely(reservationId, "RESERVATION_FAILED")
        } catch (e: IllegalStateException) {
            cancelReservationSafely(reservationId, "RESERVATION_FAILED")
        } catch (e: Exception) {
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
}