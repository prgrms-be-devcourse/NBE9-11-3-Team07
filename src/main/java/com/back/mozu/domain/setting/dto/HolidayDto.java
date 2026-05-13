package com.back.mozu.domain.setting.dto;

import com.back.mozu.domain.setting.entity.Holiday;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public class HolidayDto {

    public record HolidayItem(
            LocalDate date,
            String reason
    ) {
        public static HolidayItem from(Holiday holiday) {
            return new HolidayItem(
                    holiday.getDate(),
                    holiday.getReason()
            );
        }
    }

    public record GetHolidaysResponse(
            int totalCount,
            List<HolidayItem> holidays
    ) {
    }

    public record CreateHolidayRequest(
            @NotNull
            LocalDate date,
            String reason
    ) {
    }

    public record CreateHolidayResponse(
            LocalDate date,
            String reason,
            int conflictingReservationCount
    ) {
    }
}