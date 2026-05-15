package com.back.mozu.domain.reservation.service

import com.back.mozu.domain.reservation.entity.Reservation
import com.back.mozu.domain.reservation.entity.ReservationStatus
import com.back.mozu.domain.reservation.repository.ReservationRepository
import com.back.mozu.domain.reservation.repository.TimeSlotRepository
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Primary
import org.springframework.data.repository.findByIdOrNull
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.ZoneOffset
import java.util.UUID

@Primary
@Component
class DynamicReleaseScheduler(
    private val taskScheduler: TaskScheduler,
    private val reservationRepository: ReservationRepository,
    private val timeSlotRepository: TimeSlotRepository,
) : ReleaseScheduler, ApplicationRunner {

    // 서버 재시작하면 메모리 Task 사라지니까 DB에서 CANCEL_PENDING 다시 불러와서 재등록
    override fun run(args: ApplicationArguments) {
        reservationRepository.findAllByStatus(ReservationStatus.CANCEL_PENDING)
            .forEach { schedule(it) }
    }

    // 정확한 releaseAt 시각에 재고 반환 예약
    override fun schedule(reservation: Reservation) {
        val id = reservation.id ?: throw NoSuchElementException("예약 ID가 없습니다.")
        val releaseAt = reservation.releaseAt ?: throw NoSuchElementException("releaseAt이 없습니다.")
        taskScheduler.schedule(
            { releaseStock(id) },
            releaseAt.toInstant(ZoneOffset.UTC)
        )
    }

    @Transactional
    fun releaseStock(reservationId: UUID) {
        // DB에서 최신 상태 다시 조회해서 처리
        val reservation = reservationRepository.findByIdOrNull(reservationId)
            ?: throw NoSuchElementException("예약을 찾을 수 없습니다.")
        val timeSlotId = reservation.timeSlot?.id
            ?: throw NoSuchElementException("타임슬롯 정보가 없습니다.")
        val lockedTimeSlot = timeSlotRepository.findByIdWithLock(timeSlotId)
            ?: throw NoSuchElementException("타임슬롯을 찾을 수 없습니다.")
        lockedTimeSlot.release(reservation.guestCount)
        reservation.cancelReservation(reservation.cancelReason)
    }
}
