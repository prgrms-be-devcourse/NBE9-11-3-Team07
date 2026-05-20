package com.back.mozu.domain.setting.dto

import com.back.mozu.domain.setting.entity.Holiday
import jakarta.validation.constraints.NotNull
import java.time.LocalDate

class HolidayDto {
    data class HolidayItem(
        val date: LocalDate,
        val reason: String?
    ) {
        companion object {
            fun from(holiday: Holiday): HolidayItem {
                return HolidayItem(
                    holiday.date,
                    holiday.reason
                )
            }
        }
    }

    data class GetHolidaysResponse(
        val totalCount: Int,
        val holidays: List<HolidayItem> // MutableList -> List, non-null
    )

    data class CreateHolidayRequest(
        @field:NotNull val date: LocalDate,
        val reason: String?
    )

    data class CreateHolidayResponse(
        val date: LocalDate,
        val reason: String?,
        val conflictingReservationCount: Int
    )
}