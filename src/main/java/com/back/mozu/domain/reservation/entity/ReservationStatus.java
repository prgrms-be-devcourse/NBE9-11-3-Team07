package com.back.mozu.domain.reservation.entity;

// 예약 대기열
public enum ReservationStatus {
    PENDING, // 대기열 진입 (폴링)
    CONFIRMED, // 승인
    CANCELED, // 매진으로 인한 실패 또는 유저/관리자의 취소
    CANCEL_PENDING
}
