package com.back.mozu.domain.reservation.service

import com.back.mozu.domain.queue.service.LockService
import com.back.mozu.domain.reservation.repository.ReservationRepository
import com.back.mozu.domain.reservation.repository.TimeSlotRepository
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class ReservationAsyncProcessor(
    private val reservationRepository: ReservationRepository,
    private val timeSlotRepository: TimeSlotRepository,
    private val lockService: LockService,
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

            timeSlot.occupy(guestCount)
            stockOccupied = true

            reservation.confirmReservation()
        } catch (e: ObjectOptimisticLockingFailureException) {
            reservation.cancelReservation("OPTIMISTIC_LOCK_FAIL")
        } catch (e: IllegalArgumentException) {
            reservation.cancelReservation("RESERVATION_FAILED")
        } catch (e: IllegalStateException) {
            if (stockOccupied) {
                timeSlot.release(guestCount)
            }
            reservation.cancelReservation("RESERVATION_FAILED")
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
        } finally {
            if (lockAcquired) {
                lockService.releaseLock(timeSlotId.toString(), lockToken)
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(ReservationAsyncProcessor::class.java)
    }
}
