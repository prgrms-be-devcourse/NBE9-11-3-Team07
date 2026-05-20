package com.back.mozu.domain.setting.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(name = "holidays")
class Holiday(  // data class 금지 → 일반 class 사용 (JPA Entity는 data class 금지)
    // @NoArgsConstructor → kotlin-jpa 플러그인이 자동 생성
    // @AllArgsConstructor → 주 생성자로 대체
    // @Builder → 생성자 호출로 대체

    @Id
    @Column(nullable = false, unique = true)
    val date: LocalDate,    // @Getter 제거 → val로 getter 자동 생성
    // var? → val (PK는 변경 불가, null 아님)

    @Column(length = 255)
    val reason: String? = null  // @Getter 제거 → val로 getter 자동 생성
    // DB에서 null 가능한 컬럼 → ? 유지
)