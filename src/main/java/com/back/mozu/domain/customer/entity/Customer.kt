package com.back.mozu.domain.customer.entity

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "users")
class Customer protected constructor() {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "BINARY(16)")
    var id: UUID? = null
        protected set

    @Column(nullable = false, unique = true, length = 100)
    lateinit var email: String
        protected set

    @Column(nullable = false, length = 20)
    lateinit var provider: String
        protected set

    @Column(name = "provider_id", nullable = false, unique = true, length = 255)
    lateinit var providerId: String
        protected set

    @Column(nullable = false, length = 20)
    lateinit var role: String
        protected set

    @Column(length = 255)
    var password: String? = null
        protected set

    @Column(name = "created_at")
    var createdAt: LocalDateTime? = null
        protected set

    @Column(name = "penalty_until") //유저가 언제까지 예약이 막혀 있는지 저장하는 필드
    var penaltyUntil: LocalDateTime? = null
        protected set

    @Column(nullable = false, length = 50)
    lateinit var name: String
        protected set

    constructor(
        email: String,
        provider: String,
        providerId: String,
        role: String,
        password: String?,
        name: String
    ) : this() {
        this.email = email
        this.name = name
        this.provider = provider
        this.providerId = providerId
        this.role = role
        this.password = password
        this.createdAt = LocalDateTime.now()
    }

    fun applyPenaltyUntil(penaltyUntil: LocalDateTime) {
        this.penaltyUntil = penaltyUntil
    } // 당일 취소 시 3개월 뒤까지 예약 불가를 반영

    fun isPenaltyActive(now: LocalDateTime): Boolean {
        return penaltyUntil?.isAfter(now) ?: false
    } // 예약 시도할 때 지금 패널티가 살아 있는지 확인

    fun updateFromOAuth(name: String, email: String) {
        this.name = name
        this.email = email
    }
}