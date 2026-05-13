package com.back.mozu.domain.reservation.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "time_slots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class TimeSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;
    private LocalDate date;
    private LocalTime time;
    private int stock;

    @Version // 낙관적 락 관리 필드
    private int version;

    public void decreaseStock(int count) {
        if (this.stock < count) {
            throw new IllegalArgumentException("해당 시간대의 예약이 불가능합니다.");
        }

        this.stock -= count;
    }

    public void occupy(@Min(1) @Max(8) int guestCount) {
        // 1. 비즈니스 룰 검증
        if (guestCount < 1 || guestCount > 8) {
            throw new IllegalArgumentException("예약 인원은 최소 1명, 최대 8명까지 가능합니다.");
        }

        // 2. 재고 상태 검증
        if (this.stock < guestCount) {
            throw new IllegalArgumentException("해당 시간대의 예약 가능 인원이 부족합니다. (현재 잔여: " + this.stock + ")");
        }

        // 3. 재고 차감
        this.stock -= guestCount;
    }

    public void release(int guestCount) {
        if (guestCount < 1) {
            throw new IllegalArgumentException("반환할 인원 수는 1명 이상이어야 합니다.");
        }

        // 재고 복구
        this.stock += guestCount;
    }

    @Builder
    public TimeSlot(LocalDate date, LocalTime time, int stock) {
        this.date = date;
        this.time = time;
        this.stock = stock;
        this.version = 0;
    }
}