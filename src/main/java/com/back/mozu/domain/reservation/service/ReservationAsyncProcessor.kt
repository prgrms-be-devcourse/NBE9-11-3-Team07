package com.back.mozu.domain.reservation.service

import com.back.mozu.domain.queue.service.LockService
import com.back.mozu.domain.reservation.entity.ReservationStatus
import com.back.mozu.domain.reservation.repository.ReservationRepository
import com.back.mozu.domain.reservation.repository.TimeSlotRepository
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
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
    private val redisTemplate: RedisTemplate<String, String>,
    private val reservationStatusService: ReservationStatusService
) {

    @Async
    @Transactional(noRollbackFor = [IllegalStateException::class])
    fun processReservation(reservationId: UUID, timeSlotId: UUID, guestCount: Int) {
        val timeSlotIdStr = timeSlotId.toString()
        val lockToken = reservationId.toString()
        val occupiedKey = "isOccupied:$timeSlotIdStr"

        val isOccupied = redisTemplate.opsForValue().get(occupiedKey)?.toBoolean() ?: false
        if (isOccupied) {
            reservationStatusService.cancelReservationSafely(reservationId, "ALREADY_OCCUPIED_FAIL_FAST")
            return
        }

        var lockAcquired = false
        var stockOccupied = false

        try {
            lockAcquired = lockService.acquireLock(timeSlotIdStr, lockToken)
            if (!lockAcquired) {
                reservationStatusService.cancelReservationSafely(reservationId, "LOCK_ACQUIRE_FAIL")
                return
            }

            val reservation = reservationRepository.findByIdWithLock(reservationId)
                ?: throw IllegalArgumentException("예약 기록을 찾을 수 없습니다.")

            if (reservation.status != ReservationStatus.PENDING) {
                reservationStatusService.cancelReservationSafely(reservationId, "ALREADY_PROCESSED")
                return
            }

            val timeSlot = timeSlotRepository.findByIdWithLock(timeSlotId)
                ?: throw IllegalArgumentException("타임슬롯을 찾을 수 없습니다.")

            timeSlot.occupy(guestCount)
            stockOccupied = true

            reservation.confirmReservation()

            if (timeSlot.stock == 0) {
                redisTemplate.opsForValue().set(occupiedKey, "true")
            }

        } catch (e: ObjectOptimisticLockingFailureException) {
            reservationStatusService.cancelReservationSafely(reservationId, "OPTIMISTIC_LOCK_FAIL")

        } catch (e: IllegalArgumentException) {
            reservationStatusService.cancelReservationSafely(reservationId, "RESERVATION_FAILED")

        } catch (e: IllegalStateException) {
            if (stockOccupied) {
                timeSlotRepository.findByIdWithLock(timeSlotId)?.release(guestCount)
            }
            reservationStatusService.cancelReservationSafely(reservationId, "RESERVATION_FAILED")
            redisTemplate.delete(occupiedKey)

        } catch (e: Exception) {
            log.error(
                "비동기 예약 처리 중 시스템 예외 발생: reservationId={}, timeSlotId={}, guestCount={}",
                reservationId, timeSlotId, guestCount, e,
            )
            if (stockOccupied) {
                timeSlotRepository.findByIdWithLock(timeSlotId)?.release(guestCount)
            }
            reservationStatusService.cancelReservationSafely(reservationId, "SYSTEM_ERROR")
            redisTemplate.delete(occupiedKey)

        } finally {
            if (lockAcquired) {
                TransactionSynchronizationManager.registerSynchronization(
                    object : TransactionSynchronization {
                        override fun afterCompletion(status: Int) {
                            lockService.releaseLock(timeSlotIdStr, lockToken)
                        }
                    }
                )
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(ReservationAsyncProcessor::class.java)
    }
}