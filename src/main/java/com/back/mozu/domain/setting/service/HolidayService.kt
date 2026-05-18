package com.back.mozu.domain.setting.service

import com.back.mozu.domain.reservation.repository.ReservationRepository
import com.back.mozu.domain.setting.dto.HolidayDto.*
import com.back.mozu.domain.setting.entity.Holiday
import com.back.mozu.domain.setting.repository.HolidayRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.YearMonth

@Service
@Transactional(readOnly = true)
// @RequiredArgsConstructor 제거 → 코틀린 생성자 주입으로 대체
class HolidayService(
    private val holidayRepository: HolidayRepository,
    private val reservationRepository: ReservationRepository
) {

    fun getHolidays(month: String?): GetHolidaysResponse {
        // MutableList<Holiday?>? 제거 → 타입 추론으로 대체
        // if-else 표현식으로 변환 (Java의 if문 → Kotlin의 if 표현식)
        val holidays = if (month == null || month.isBlank()) {
            // stream().sorted() → sortedBy()로 대체
            holidayRepository.findAll().sortedBy { it.date }
        } else {
            val yearMonth = YearMonth.parse(month)
            holidayRepository.findByDateBetweenOrderByDateAsc(
                yearMonth.atDay(1),
                yearMonth.atEndOfMonth()
            )
        }
        // stream().map().toList() → map {}으로 대체
        val items = holidays.map { HolidayItem.from(it) }

        return GetHolidaysResponse(items.size, items)
    }

    @Transactional
    fun createHoliday(request: CreateHolidayRequest): CreateHolidayResponse {
        validateCreateRequest(request)

        // Builder 패턴 제거 → named argument로 대체
        val savedHoliday = holidayRepository.save(
            Holiday(date = request.date, reason = request.reason)
        )
        // !! 제거 → 생성자 주입으로 non-null 보장
        val conflictingReservationCount = reservationRepository.countByTimeSlot_Date(savedHoliday.date)

        return CreateHolidayResponse(
            savedHoliday.date,
            savedHoliday.reason,
            conflictingReservationCount
        )
    }

    private fun validateCreateRequest(request: CreateHolidayRequest) {
        require(!request.date.isBefore(LocalDate.now())) { "과거 날짜는 휴무일로 설정할 수 없습니다." }
        check(!holidayRepository.existsById(request.date)) { "이미 등록된 휴무일입니다." }
    }
}