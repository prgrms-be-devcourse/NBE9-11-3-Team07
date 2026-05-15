package com.back.mozu.domain.reservation.service

import com.back.mozu.domain.customer.service.CustomerService
import com.back.mozu.domain.reservation.dto.ReservationDto
import com.back.mozu.domain.reservation.dto.ReservationDto.Response.Companion.from
import com.back.mozu.domain.reservation.entity.ReservationStatus
import com.back.mozu.domain.reservation.repository.ReservationRepository
import com.back.mozu.domain.reservation.repository.TimeSlotRepository
import com.back.mozu.global.exception.ServiceException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Service
@Transactional(readOnly = true)
class ReservationService(
    private val reservationRepository: ReservationRepository,
    private val timeSlotRepository: TimeSlotRepository,
    private val releaseScheduler: ReleaseScheduler,
    private val customerService: CustomerService,
) {

    fun getMyReservation(customerId: UUID): List<ReservationDto.Response> =
        reservationRepository.findAllByUserId(customerId)
            .map { ReservationDto.Response.from(it) }

    @Transactional
    fun modifyMyReservation(
        reservationId: UUID,
        customerId: UUID?,
        request: ReservationDto.Request
    ): ReservationDto.Response {
        val userId = customerId ?: throw ServiceException.Common("401-1", "인증이 필요합니다.")
        val reservation = reservationRepository.findByIdOrNull(reservationId)
            ?: throw ServiceException.Common("404-1", "예약을 찾을 수 없습니다.")

        if (reservation.userId != userId) {
            throw ServiceException.Common("403-1", "해당 예약을 수정할 권한이 없습니다.")
        }

        val date = request.date ?: throw ServiceException.Common("400-1", "날짜를 입력해주세요.")
        val time = request.time ?: throw ServiceException.Common("400-1", "시간을 입력해주세요.")

        val newTimeSlot = timeSlotRepository.findByDateAndTime(date, time)
            ?: throw ServiceException.Common("404-1", "해당 시간대는 존재하지 않습니다.")
        val newTimeSlotId = newTimeSlot.id ?: throw NoSuchElementException("타임슬롯 ID가 없습니다.")
        val lockedNewTimeSlot = timeSlotRepository.findByIdWithLock(newTimeSlotId)
            ?: throw NoSuchElementException("타임슬롯을 찾을 수 없습니다.")
        lockedNewTimeSlot.occupy(request.guestCount)

        val oldTimeSlotId = reservation.timeSlot?.id
            ?: throw NoSuchElementException("기존 타임슬롯 정보가 없습니다.")
        val lockedOldTimeSlot = timeSlotRepository.findByIdWithLock(oldTimeSlotId)
            ?: throw NoSuchElementException("기존 타임슬롯을 찾을 수 없습니다.")
        lockedOldTimeSlot.release(reservation.guestCount)

        reservation.modifyReservation(lockedNewTimeSlot, request.guestCount)
        return from(reservation)
    }

    @Transactional
    fun cancelMyReservation(customerId: UUID?, reservationId: UUID, cancelReason: String?): ReservationDto.Response {
        val userId = customerId ?: throw ServiceException.Common("401-1", "인증이 필요합니다.")
        val reservation = reservationRepository.findByIdOrNull(reservationId)
            ?: throw ServiceException.Common("404-1", "예약을 찾을 수 없습니다.")

        if (reservation.userId != userId) {
            throw ServiceException.Common("403-1", "해당 예약을 취소할 권한이 없습니다.")
        }
        require(reservation.status != ReservationStatus.CANCELED) { "이미 취소된 예약입니다." }

        val now = LocalDateTime.now()
        val timeSlot = reservation.timeSlot ?: throw NoSuchElementException("타임슬롯 정보가 없습니다.")
        val reservationDateTime = LocalDateTime.of(
            timeSlot.date ?: throw NoSuchElementException("타임슬롯 날짜가 없습니다."),
            timeSlot.time ?: throw NoSuchElementException("타임슬롯 시간이 없습니다.")
        )

        var effectiveCancelReason = cancelReason
        if (reservationDateTime.isBefore(now.plusHours(24))) {
            val customer = customerService.findByIdOrNull(userId)
                ?: throw ServiceException.Common("404-1", "사용자를 찾을 수 없습니다.")
            customer.applyPenaltyUntil(now.plusMonths(3))
            effectiveCancelReason = "LATE_CANCEL"
        }

        // [케이스 1] 오픈 직후 취소: 예약 오픈 후 30분 이내 취소 → 암표 의심 → 랜덤 반환 적용
        val isEarlyCancel = reservation.reservationOpenedAt?.let {
            now.isBefore(it.plusMinutes(30))
        } ?: false

        // [케이스 2] 반복 취소 유저: 같은 유저가 3개월 내 3번 이상 취소 → 어뷰징 의심 → 랜덤 반환 적용
        val isRepeatCancel = reservationRepository.countByUserIdAndStatusAndCancelledAtAfter(
            userId, ReservationStatus.CANCELED, now.minusMonths(3)
        ) >= 3

        if (isEarlyCancel || isRepeatCancel) {
            val randomMinutes = (10..60).random()
            val releaseAt = now.plusMinutes(randomMinutes.toLong())
            reservation.pendingCancel(effectiveCancelReason, releaseAt)
            releaseScheduler.schedule(reservation)
        } else {
            val timeSlotId = timeSlot.id ?: throw NoSuchElementException("타임슬롯 ID가 없습니다.")
            val lockedTimeSlot = timeSlotRepository.findByIdWithLock(timeSlotId)
                ?: throw NoSuchElementException("타임슬롯을 찾을 수 없습니다.")
            lockedTimeSlot.release(reservation.guestCount)
            reservation.cancelReservation(effectiveCancelReason)
        }

        return from(reservation)
    }
}
