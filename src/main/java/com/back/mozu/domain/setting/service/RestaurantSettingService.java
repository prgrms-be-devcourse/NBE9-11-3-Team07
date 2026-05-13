package com.back.mozu.domain.setting.service;

import com.back.mozu.domain.reservation.entity.TimeSlot;
import com.back.mozu.domain.reservation.repository.TimeSlotRepository;
import com.back.mozu.domain.setting.dto.SettingUpdateRequestDto;
import com.back.mozu.domain.setting.dto.SettingUpdateResponseDto;
import com.back.mozu.domain.setting.entity.Holiday;
import com.back.mozu.domain.setting.entity.RestaurantSettings;
import com.back.mozu.domain.setting.repository.HolidayRepository;
import com.back.mozu.domain.setting.repository.RestaurantSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RestaurantSettingService {

    private final RestaurantSettingRepository restaurantSettingRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final HolidayRepository holidayRepository;

    @Transactional
    public SettingUpdateResponseDto updateSettings(SettingUpdateRequestDto dto) {
        RestaurantSettings settings = restaurantSettingRepository.findById(1)
                .orElseThrow(() -> new RuntimeException("설정값을 찾을 수 없습니다"));
        settings.update(dto.getTotalTables(), dto.getOpeningTime(), dto.getClosingTime());
        restaurantSettingRepository.save(settings);

        generateNextMonthTimeSlots(dto.getTotalTables(), dto.getOpeningTime(), dto.getClosingTime());

        return SettingUpdateResponseDto.builder()
                .totalTables(settings.getTotalTables())
                .openingTime(settings.getOpeningTime())
                .closingTime(settings.getClosingTime())
                .build();
    }

    private void generateNextMonthTimeSlots(int totalTables, LocalTime openingTime, LocalTime closingTime) {
        LocalDate today = LocalDate.now();
        LocalDate firstDayOfNextMonth = today.withDayOfMonth(1).plusMonths(1);
        LocalDate lastDayOfNextMonth = firstDayOfNextMonth.withDayOfMonth(firstDayOfNextMonth.lengthOfMonth());

        // 다음달 휴무일 조회
        List<Holiday> holidays = holidayRepository.findByDateBetweenOrderByDateAsc(firstDayOfNextMonth, lastDayOfNextMonth);
        Set<LocalDate> holidayDates = holidays.stream()
                .map(Holiday::getDate)
                .collect(Collectors.toSet());

        List<TimeSlot> timeSlots = new ArrayList<>();

        for (LocalDate date = firstDayOfNextMonth; !date.isAfter(lastDayOfNextMonth); date = date.plusDays(1)) {
            // 휴무일 제외
            if (holidayDates.contains(date)) continue;

            LocalTime time = openingTime;
            while (!time.isAfter(closingTime)) {
                LocalDate finalDate = date;
                LocalTime finalTime = time;
                boolean exists = timeSlotRepository.findByDateAndTime(finalDate, finalTime).isPresent();
                if (!exists) {
                    timeSlots.add(TimeSlot.builder()
                            .date(date)
                            .time(time)
                            .stock(totalTables)
                            .build());
                }
                time = time.plusMinutes(30);
            }
        }

        timeSlotRepository.saveAll(timeSlots);
    }

    @Transactional
    public void setup(Integer totalTables, LocalTime openingTime, LocalTime closingTime) {
        if (restaurantSettingRepository.count() > 0) return;
        restaurantSettingRepository.save(
                RestaurantSettings.builder()
                        .totalTables(totalTables)
                        .openingTime(openingTime)
                        .closingTime(closingTime)
                        .build()
        );
    }
}