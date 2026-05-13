package com.back.mozu.global.initData;

import com.back.mozu.domain.setting.service.RestaurantSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;

@Configuration
@RequiredArgsConstructor
@Profile("dev")
public class BaseInitData {

    @Autowired
    @Lazy
    private BaseInitData self;
    private final RestaurantSettingService restaurantSettingService;
//    private final UserService userService;
//    private final TimeSlotService timeSlotService;
//    private final HolidayService holidayService;

    @Bean
    public ApplicationRunner initData() {
        return args -> {
            self.work1();
//            self.work2();
        };
    }

    @Transactional
    public void work1() {
        restaurantSettingService.setup(10, LocalTime.of(11, 0), LocalTime.of(22, 0));
//        if (userService.count() > 0) return;
//        userService.join("customer@test.com", "1234", "USER");
//        userService.join("admin@test.com", "1234", "ADMIN");
//        int[] sundays = {3, 10, 17, 24, 31};
//        int[] mondays = {4, 11, 18, 25};
//        for (int d : sundays) holidayService.create(LocalDate.of(2026, 5, d), "정기 휴무(일)");
//        for (int d : mondays) holidayService.create(LocalDate.of(2026, 5, d), "정기 휴무(월)");
    }

//    @Transactional
//    public void work2() {
//        if (timeSlotService.count() > 0) return;
//        LocalDate start = LocalDate.of(2026, 5, 1);
//        LocalDate end = LocalDate.of(2026, 5, 31);
//        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
//            if (holidayService.isHoliday(date)) continue;
//            generateTimeSlots(date, LocalTime.of(12, 0), LocalTime.of(14, 0));
//            generateTimeSlots(date, LocalTime.of(17, 0), LocalTime.of(20, 0));
//        }
//    }

//    private void generateTimeSlots(LocalDate date, LocalTime start, LocalTime end) {
//        LocalTime curr = start;
//        while (!curr.isAfter(end)) {
//            timeSlotService.create(date, curr, 10);
//            curr = curr.plusMinutes(30);
//        }
//    }

}