package com.back.mozu.test;

import com.back.mozu.domain.customer.entity.Customer;
import com.back.mozu.domain.customer.repository.CustomerRepository;
import com.back.mozu.domain.reservation.entity.TimeSlot;
import com.back.mozu.domain.reservation.repository.TimeSlotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@TestConfiguration
@Slf4j
public class MozuDataSeeder implements CommandLineRunner {

    private final CustomerRepository customerRepository;
    private final TimeSlotRepository timeSlotRepository;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        // 데이터가 이미 있으면 시딩 건너뛰기
        if (customerRepository.count() > 0) {
            log.info("이미 데이터가 존재하여 시딩을 건너뜁니다.");
            return;
        }

        log.info(">>> 데이터 시딩 시작 (약 10만 건)... 이 작업은 수 분이 소요될 수 있습니다.");

        // 1. 타임슬롯 시딩 (향후 100일간, 매일 5개 타임, 각 타임당 30석)
        List<TimeSlot> timeSlots = new ArrayList<>();
        LocalDate startDate = LocalDate.now();
        for (int i = 0; i < 100; i++) {
            LocalDate targetDate = startDate.plusDays(i);
            for (int hour = 17; hour <= 21; hour++) { // 오후 5시 ~ 9시
                timeSlots.add(TimeSlot.builder()
                        .date(targetDate)
                        .time(LocalTime.of(hour, 0))
                        .stock(30)
                        .build());
            }
        }
        timeSlotRepository.saveAll(timeSlots);
        log.info(">>> 타임슬롯 500개 생성 완료");

        // 2. 유저 시딩 (10만 명)
        // 성능을 위해 1,000명 단위로 끊어서 저장 (Batch Insert 효과)
        int totalCustomers = 100_000;
        int batchSize = 1000;
        List<Customer> batch = new ArrayList<>();

        for (int i = 1; i <= totalCustomers; i++) {
            batch.add(Customer.builder()
                    .email("user" + i + "@test.com")
                    .provider("google")
                    .providerId("google_" + i)
                    .role("USER")
                    .name("테스트")
                    .password(null) // 소셜 로그인 가정
                    .build());

            if (i % batchSize == 0) {
                customerRepository.saveAll(batch);
                batch.clear();
                log.info(">>> 유저 시딩 중... {}/{}", i, totalCustomers);
            }
        }

        log.info(">>> 모든 데이터 시딩 완료! 이제 '딸깍' 테스트를 시작할 준비가 되었습니다.");
    }
}
