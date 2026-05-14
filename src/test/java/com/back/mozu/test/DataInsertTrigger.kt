package com.back.mozu.test

import com.back.mozu.domain.customer.entity.Customer
import com.back.mozu.domain.customer.repository.CustomerRepository
import com.back.mozu.domain.reservation.entity.TimeSlot
import com.back.mozu.domain.reservation.repository.TimeSlotRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import java.time.LocalDate
import java.time.LocalTime

@SpringBootTest
@TestPropertySource(
    properties = [
        "GOOGLE_CLIENT_ID=dummy",
        "GOOGLE_CLIENT_SECRET=dummy",
        "spring.datasource.url=jdbc:mysql://localhost:3306/moju_db?serverTimezone=Asia/Seoul&useSSL=false&allowPublicKeyRetrieval=true",
        "spring.datasource.username=root",
        "spring.datasource.password=root",
        "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect",
        "spring.jpa.hibernate.ddl-auto=update"
    ]
)
class DataInsertTrigger {
    // @Value와 마찬가지로 @Autowired도 스프링이 나중에 빈 주입함 -> lateinit var 사용
    // 생성자 주입은 테스트클래스에서 잘 안되는 경우 있어서 @Autowired 사용
    @Autowired
    private lateinit var customerRepository: CustomerRepository

    @Autowired
    private lateinit var timeSlotRepository: TimeSlotRepository

    @Test
    fun `타임슬롯과 유저 더미 데이터를 생성한다`() {
        println(">>> [START] 데이터 시딩 프로세스 시작")

        // 1. 타임슬롯 생성 (약 500개)
        if (timeSlotRepository.count() == 0L) {
            val slots = mutableListOf<TimeSlot>()   // 타입명시 안해도 됨 (mutableListOf<TimeSlot>에서 타입 명확히 드러남)
                                                    // 실제 TimeSlot 객체에서 null 없이 객체만 넣고 있가 때문에 ? 빼도 됨
            val date = LocalDate.now()
            for (i in 0..99) {
                for (hour in 17..21) {
                    slots.add(
                        TimeSlot(
                            date = date.plusDays(i.toLong()),   // 코틀린은 builder 대신 생성자에 named argument를 사용
                            time = LocalTime.of(hour, 0),
                            stock = 30
                        )
                    )
                }
            }
            timeSlotRepository.saveAll(slots)
            println(">>> 타임슬롯 생성 완료 (500건)")
        }

        // 2. 유저 생성 (10만 명)
        val currentCount = customerRepository.count()
        if (currentCount < 100000) {
            val total = 100000
            val batchSize = 1000
            val batch = mutableListOf<Customer>()   // 타입명시 안해도 됨 (mutableListOf<Customer>에서 타입 명확히 드러남)
                                                    // 실제 Customer 객체에서 null 없이 객체만 넣고 있가 때문에 ? 빼도 됨
            for (i in currentCount.toInt() + 1..total) {
                batch.add(
                    Customer(
                        email = "user$i@test.com",  //
                        name = "테스터$i",
                        provider = "google",
                        providerId = "google$i",
                        role = "USER",
                        password = null
                    )
                )

                if (i % batchSize == 0) {
                    customerRepository.saveAll(batch)   // batch가 이미 MutableList<Customer> 이므로 타입 추론 가능
                    batch.clear()
                    println(">>> 진행 중: $i / $total")
                }
            }
        }

        println(">>> [SUCCESS] 모든 더미 데이터가 준비되었습니다. '딸깍' 성공!")
    }
}