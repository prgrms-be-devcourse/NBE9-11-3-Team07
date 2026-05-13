package com.back.mozu.domain.setting.service;

import com.back.mozu.domain.reservation.repository.ReservationRepository;
import com.back.mozu.domain.setting.dto.HolidayDto;
import com.back.mozu.domain.setting.entity.Holiday;
import com.back.mozu.domain.setting.repository.HolidayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HolidayService {

    private final HolidayRepository holidayRepository;
    private final ReservationRepository reservationRepository;

    public HolidayDto.GetHolidaysResponse getHolidays(String month) {
        List<Holiday> holidays;

        if (month == null || month.isBlank()) {
            holidays = holidayRepository.findAll()
                    .stream()
                    .sorted((a, b) -> a.getDate().compareTo(b.getDate()))
                    .toList();
        } else {
            YearMonth yearMonth = YearMonth.parse(month);
            LocalDate startDate = yearMonth.atDay(1);
            LocalDate endDate = yearMonth.atEndOfMonth();
            holidays = holidayRepository.findByDateBetweenOrderByDateAsc(startDate, endDate);
        }

        List<HolidayDto.HolidayItem> items = holidays.stream()
                .map(HolidayDto.HolidayItem::from)
                .toList();

        return new HolidayDto.GetHolidaysResponse(items.size(), items);
    }

    @Transactional
    public HolidayDto.CreateHolidayResponse createHoliday(HolidayDto.CreateHolidayRequest request) {
        validateCreateRequest(request);

        Holiday holiday = Holiday.builder()
                .date(request.date())
                .reason(request.reason())
                .build();

        Holiday savedHoliday = holidayRepository.save(holiday);
        int conflictingReservationCount = reservationRepository.countByTimeSlot_Date(savedHoliday.getDate());

        return new HolidayDto.CreateHolidayResponse(
                savedHoliday.getDate(),
                savedHoliday.getReason(),
                conflictingReservationCount
        );
    }

    private void validateCreateRequest(HolidayDto.CreateHolidayRequest request) {
        if (request.date().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("과거 날짜는 휴무일로 설정할 수 없습니다.");
        }

        if (holidayRepository.existsById(request.date())) {
            throw new IllegalStateException("이미 등록된 휴무일입니다.");
        }
    }
}

