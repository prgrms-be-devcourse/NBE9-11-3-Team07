package com.back.mozu.test;

import com.back.mozu.domain.customer.entity.Customer;
import com.back.mozu.domain.customer.repository.CustomerRepository;
import com.back.mozu.domain.reservation.entity.TimeSlot;
import com.back.mozu.domain.reservation.repository.TimeSlotRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@SpringBootTest
@TestPropertySource(properties = {
        "GOOGLE_CLIENT_ID=dummy",
        "GOOGLE_CLIENT_SECRET=dummy",
        "spring.datasource.url=jdbc:mysql://localhost:3306/moju_db?serverTimezone=Asia/Seoul&useSSL=false&allowPublicKeyRetrieval=true",
        "spring.datasource.username=root",
        "spring.datasource.password=root",
        "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect",
        "spring.jpa.hibernate.ddl-auto=update"
})
public class DataInsertTrigger {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private TimeSlotRepository timeSlotRepository;

    @Test
    void fastSeeding() {
        System.out.println(">>> [START] 데이터 시딩 프로세스 시작");

        // 1. 타임슬롯 생성 (약 500개)
        if (timeSlotRepository.count() == 0) {
            List<TimeSlot> slots = new ArrayList<>();
            LocalDate date = LocalDate.now();
            for (int i = 0; i < 100; i++) {
                for (int hour = 17; hour <= 21; hour++) {
                    slots.add(TimeSlot.builder()
                            .date(date.plusDays(i))
                            .time(LocalTime.of(hour, 0))
                            .stock(30)
                            .build());
                }
            }
            timeSlotRepository.saveAll(slots);
            System.out.println(">>> 타임슬롯 생성 완료 (500건)");
        }

        // 2. 유저 생성 (10만 명)
        long currentCount = customerRepository.count();
        if (currentCount < 100000) {
            int total = 100000;
            int batchSize = 1000;
            List<Customer> batch = new ArrayList<>();

            for (int i = (int)currentCount + 1; i <= total; i++) {
                batch.add(Customer.builder()
                        .email("user" + i + "@test.com")
                        .name("테스터" + i)
                        .provider("google")
                        .providerId("google_" + i)
                        .role("USER")
                        .build());

                if (i % batchSize == 0) {
                    customerRepository.saveAll(batch);
                    batch.clear();
                    System.out.println(">>> 진행 중: " + i + " / " + total);
                }
            }
        }

        System.out.println(">>> [SUCCESS] 모든 더미 데이터가 준비되었습니다. '딸깍' 성공!");
    }
}