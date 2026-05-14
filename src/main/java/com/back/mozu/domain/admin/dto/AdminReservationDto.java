package com.back.mozu.domain.admin.dto;

import com.back.mozu.domain.customer.entity.Customer;
import com.back.mozu.domain.reservation.entity.Reservation;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter
public class AdminReservationDto {

    private String reservationId;
    private String userId;
    private String userName;
    private String userEmail;
    private LocalDate date;
    private LocalTime time;
    private int guestCount;
    private String status;
    private String cancelReason;
    private LocalDateTime createdAt;

    public AdminReservationDto(Reservation reservation, Customer customer) {
        this.reservationId = reservation.getId().toString(); // UUID → String
        this.userId = reservation.getUserId().toString();
        this.userName = customer.getName();   // Customer 엔티티에서
        this.userEmail = customer.getEmail();// UUID → String
        this.date = reservation.getTimeSlot().getDate();
        this.time = reservation.getTimeSlot().getTime();
        this.guestCount = reservation.getGuestCount();
        this.status = reservation.getStatus().name();        // Enum → String
        this.createdAt = reservation.getCreatedAt();
        this.cancelReason = reservation.getCancelReason();
    }
}