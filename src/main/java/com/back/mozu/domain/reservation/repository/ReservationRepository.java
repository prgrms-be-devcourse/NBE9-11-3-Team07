package com.back.mozu.domain.reservation.repository;

import com.back.mozu.domain.reservation.entity.Reservation;
import com.back.mozu.domain.reservation.entity.ReservationStatus;
import com.back.mozu.domain.reservation.entity.TimeSlot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {
    Optional<Reservation> findByUserIdAndStatus(UUID customerId, ReservationStatus status);
    boolean existsByUserIdAndTimeSlotAndStatusNot(UUID customerId, TimeSlot timeSlot, ReservationStatus status);
    List<Reservation> findAllByUserId(UUID customerId);
    int countByTimeSlot_Date(LocalDate date);
    Page<Reservation> findAllWithFilters(LocalDate date, LocalTime time, String status, Pageable pageable);

    // 추가
    int countByUserIdAndStatusAndCancelledAtAfter(UUID userId, ReservationStatus status, LocalDateTime dateTime);
    List<Reservation> findAllByStatusAndReleaseAtBefore(ReservationStatus status, LocalDateTime dateTime);
    List<Reservation> findAllByStatusAndCreatedAtBefore(ReservationStatus status, LocalDateTime createdAt);
    List<Reservation> findAllByStatus(ReservationStatus status);
    List<Reservation> findByTimeSlotIdAndStatusOrderByCreatedAt(UUID timeSlotId, ReservationStatus status);
}