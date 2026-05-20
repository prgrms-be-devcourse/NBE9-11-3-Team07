package com.back.mozu.test

import com.back.mozu.domain.customer.entity.Customer
import com.back.mozu.domain.customer.repository.CustomerRepository
import com.back.mozu.domain.reservation.entity.TimeSlot
import com.back.mozu.domain.reservation.repository.TimeSlotRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalTime

@Component
@TestConfiguration
class MozuDataSeeder(
    private val customerRepository: CustomerRepository, // @RequeiredArgustConsturctor 대신 생성자 주입
    private val timeSlotRepository: TimeSlotRepository

) : CommandLineRunner {

    // @Slf4j는 Lombok 어노테이션 -> Lombok이 읽고 자동으로 log 필드를 자동생성 해줌
    private val log = LoggerFactory.getLogger(javaClass) //  @Slf4j 제거 → logger 직접 선언

    @Transactional
    @Throws(Exception::class)
    override fun run(vararg args: String) {
        // 데이터가 이미 있으면 시딩 건너뛰기
        if (customerRepository.count() > 0) {   // !! 제거 -> 생성자 주입으로 대체
            log.info("이미 데이터가 존재하여 시딩을 건너뜁니다.")
            return
        }

        log.info(">>> 데이터 시딩 시작 (약 10만 건)... 이 작업은 수 분이 소요될 수 있습니다.")

        // 1. 타임슬롯 시딩 (향후 100일간, 매일 5개 타임, 각 타임당 30석)
        val timeSlots = mutableListOf<TimeSlot>()
        val startDate = LocalDate.now()
        for (i in 0..99) {
            val targetDate = startDate.plusDays(i.toLong())
            for (hour in 17..21) { // 오후 5시 ~ 9시
                timeSlots.add(
                    TimeSlot(
                        date = targetDate,  // Builder → 생성자 + named argument
                        time = LocalTime.of(hour, 0),
                        stock = 30
                    )
                )
            }
        }
        timeSlotRepository.saveAll(timeSlots)   // 타입 명시 제거 → 타입 추론
        log.info(">>> 타임슬롯 500개 생성 완료")

        // 2. 유저 시딩 (10만 명)
        // 성능을 위해 1,000명 단위로 끊어서 저장 (Batch Insert 효과)
        val totalCustomers = 100000
        val batchSize = 1000
        val batch = mutableListOf<Customer>()   // ArrayList -> mutableListOf, ? 제거

        for (i in 1..totalCustomers) {
            batch.add(
                Customer(
                    email = "user$i@test.com",  // Builder -> 생성자 + named + argument
                    provider = "google",
                    providerId = "google_$i",   // 문자열 템플릿
                    role = "USER",
                    name = "테스트",
                    password = null // 소셜 로그인 가정
                )
            )

            if (i % batchSize == 0) {
                customerRepository.saveAll(batch)   // 타입 명시 제거 -> 타입 추론
                batch.clear()
                log.info(">>> 유저 시딩 중... {}/{}", i, totalCustomers)
            }
        }

        log.info(">>> 모든 데이터 시딩 완료! 이제 '딸깍' 테스트를 시작할 준비가 되었습니다.")
    }
}