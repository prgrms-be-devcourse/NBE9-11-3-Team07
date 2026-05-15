package com.back.mozu.domain.setting.service

import com.back.mozu.domain.reservation.entity.TimeSlot
import com.back.mozu.domain.reservation.repository.TimeSlotRepository
import com.back.mozu.domain.setting.dto.SettingUpdateRequestDto
import com.back.mozu.domain.setting.dto.SettingUpdateResponseDto
import com.back.mozu.domain.setting.entity.RestaurantSettings
import com.back.mozu.domain.setting.repository.HolidayRepository
import com.back.mozu.domain.setting.repository.RestaurantSettingRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalTime

@Service
class RestaurantSettingService(
    private val restaurantSettingRepository: RestaurantSettingRepository,   // @RequiredArgsConstructor 제거 → 코틀린 생성자 주입으로 대체
    private val timeSlotRepository: TimeSlotRepository,
    private val holidayRepository: HolidayRepository
) {

    @Transactional
    fun updateSettings(dto: SettingUpdateRequestDto): SettingUpdateResponseDto {
        val settings = restaurantSettingRepository.findByIdOrNull(1)
            ?: throw RuntimeException("설정값을 찾을 수 없습니다") // Optional.orElseThrow() → findByIdOrNull() ?: throw 로 대체
        // null이면 기존 값 유지 (PATCH 방식) → ?: 엘비스 연산자로 처리
        settings.update(
            dto.totalTables ?: settings.totalTables,
            dto.openingTime ?: settings.openingTime,
            dto.closingTime ?: settings.closingTime
        )
        restaurantSettingRepository.save(settings)

        generateNextMonthTimeSlots(
            dto.totalTables ?: settings.totalTables,
            dto.openingTime ?: settings.openingTime,
            dto.closingTime ?: settings.closingTime
        )

        return SettingUpdateResponseDto(
            totalTables = settings.totalTables, // Builder 패턴 제거 → named argument로 대체
            openingTime = settings.openingTime,
            closingTime = settings.closingTime
        )
    }

    private fun generateNextMonthTimeSlots(totalTables: Int, openingTime: LocalTime, closingTime: LocalTime) {
        val today = LocalDate.now()
        val firstDayOfNextMonth = today.withDayOfMonth(1).plusMonths(1)
        val lastDayOfNextMonth = firstDayOfNextMonth.withDayOfMonth(firstDayOfNextMonth.lengthOfMonth())

        // 다음달 휴무일 조회
        // stream().map().collect(toSet()) → map {}.toSet() 으로 대체
        val holidayDates = holidayRepository
            .findByDateBetweenOrderByDateAsc(firstDayOfNextMonth, lastDayOfNextMonth)
            .map { it.date }
            .toSet()

        // ArrayList() → mutableListOf() 로 대체
        val timeSlots = mutableListOf<TimeSlot>()

        var date = firstDayOfNextMonth
        while (!date.isAfter(lastDayOfNextMonth)) {
            // 휴무일 제외
            // Optional.isPresent() → == null 로 대체
            // finalDate, finalTime 제거 → Kotlin은 effectively final 제약 없음
            if (holidayDates.contains(date)) {
                date = date.plusDays(1)
                continue
            }

            var time = openingTime
            while (!time.isAfter(closingTime)) {
                if (timeSlotRepository.findByDateAndTime(date, time) == null) {
                    timeSlots.add(
                        TimeSlot(
                            date = date,    // Builder 패턴 제거 → named argument로 대체
                            time = time,
                            stock = totalTables
                        )

                    )
                }
                time = time.plusMinutes(30)
            }
            date = date.plusDays(1)
        }

        timeSlotRepository.saveAll(timeSlots)
    }

    @Transactional
    fun setup(totalTables: Int?, openingTime: LocalTime?, closingTime: LocalTime?) {
        if (restaurantSettingRepository.count() > 0) return
        // Builder 패턴 제거 → named argument로 대체
        // null이면 기본값 세팅 → ?: 엘비스 연산자로 처리
        restaurantSettingRepository.save(
            RestaurantSettings(
                totalTables = totalTables ?: 0,
                openingTime = openingTime ?: LocalTime.of(9, 0),
                closingTime = closingTime ?: LocalTime.of(21, 0),
            )
        )
    }
}