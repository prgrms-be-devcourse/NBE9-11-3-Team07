package com.back.mozu.domain.reservation.service

import com.back.mozu.domain.queue.service.LockService
import com.back.mozu.domain.reservation.repository.ReservationRepository
import com.back.mozu.domain.reservation.repository.TimeSlotRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.slf4j.LoggerFactory
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
        val lockToken: String = reservationId.toString()
        var lockAcquired = false
        try {
            lockAcquired = lockService.acquireLock(timeSlotId.toString(), lockToken)
            if (!lockAcquired) {
                reservation.cancelReservation("LOCK_ACQUIRE_FAIL")
                return
            }

            timeSlot.occupy(guestCount)
            reservation.confirmReservation()
        } catch (e: ObjectOptimisticLockingFailureException) {
            // [CASE A] 다른 유저가 찰나의 순간에 먼저 가져감 (낙관적 락 충돌) → 재고는 안 깎혔으므로 예약만 취소
            reservation.cancelReservation("OPTIMISTIC_LOCK_FAIL")
        } catch (e: IllegalArgumentException) {
            // [CASE B] 재고 부족이거나 이미 취소된 건 → occupy에서 실패했으므로 재고 건드리지 않음
            reservation.cancelReservation("RESERVATION_FAILED")
        } catch (e: IllegalStateException) {
            reservation.cancelReservation("RESERVATION_FAILED")
        } catch (e: Exception) {
            log.error(
                "비동기 예약 처리 중 시스템 예외 발생: reservationId={}, timeSlotId={}, guestCount={}",
                reservationId,
                timeSlotId,
                guestCount,
                e,
            )
            reservation.cancelReservation("SYSTEM_ERROR")
            timeSlot.release(guestCount)
        } finally {
            // 데드락 방지: 성공/실패 무관 락 해제
            if (lockAcquired) {
                lockService.releaseLock(timeSlotId.toString(), lockToken)
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(ReservationAsyncProcessor::class.java)
    }
}
