package com.back.mozu.domain.setting.entity

import jakarta.persistence.*
import java.time.LocalTime

@Entity
@Table(name = "restaurant_settings")
class RestaurantSettings(   // data class 금지 → 일반 class 사용
    // @NoArgsConstructor → kotlin-jpa 플러그인이 자동 생성
    // @AllArgsConstructor → 주 생성자로 대체
    // @Builder → 생성자 호출로 대체

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int? = null,        // PK이지만 DB 자동생성이라 저장 전 null 가능 → ? 유지

    @Column(nullable = false)
    var totalTables: Int,       // @Getter 제거 → var로 getter 자동 생성
    // update()에서 변경 가능 → var 사용
    // nullable = false → ? 제거

    @Column(nullable = false)
    var openingTime: LocalTime, // update()에서 변경 가능 → var 사용

    @Column(nullable = false)
    var closingTime: LocalTime  // update()에서 변경 가능 → var 사용

) {
    fun update(totalTables: Int, openingTime: LocalTime, closingTime: LocalTime) {
        this.totalTables = totalTables      // var이라 변경 가능
        this.openingTime = openingTime
        this.closingTime = closingTime
    }
}