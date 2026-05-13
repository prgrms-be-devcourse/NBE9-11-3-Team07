package com.back.mozu.domain.customer.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false, length = 20)
    private String provider;

    @Column(name = "provider_id", nullable = false, unique = true, length = 255)
    private String providerId;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(length = 255)
    private String password;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "penalty_until")
    private LocalDateTime penaltyUntil; //유저가 언제까지 예약이 막혀 있는지 저장하는 필드

    @Column(nullable = false, length = 50)
    private String name;

    @Builder
    public Customer(String email, String provider, String providerId, String role, String password, String name) {
        this.email = email;
        this.name = name;
        this.provider = provider;
        this.providerId = providerId;
        this.role = role;
        this.password = password;
        this.createdAt = LocalDateTime.now();
    }

    public void applyPenaltyUntil(LocalDateTime penaltyUntil) {
        this.penaltyUntil = penaltyUntil;
    } // 당일 취소 시 3개월 뒤까지 예약 불가를 반영

    public boolean isPenaltyActive(LocalDateTime now) {
        return penaltyUntil != null && penaltyUntil.isAfter(now);
    } // 예약 시도할 때 지금 패널티가 살아 있는지 확인

    public void updateFromOAuth(String name, String email) {
        this.name = name;
        this.email = email;
    }
}