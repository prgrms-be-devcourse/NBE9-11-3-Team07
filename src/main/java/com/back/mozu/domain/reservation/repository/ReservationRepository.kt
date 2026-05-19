package com.back.mozu.domain.reservation.repository

import com.back.mozu.domain.reservation.entity.Reservation
import com.back.mozu.domain.reservation.entity.ReservationStatus
import com.back.mozu.domain.reservation.entity.TimeSlot
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

interface ReservationRepository : JpaRepository<Reservation, UUID> {
    fun findByUserIdAndStatus(userId: UUID, status: ReservationStatus): Reservation?
    fun existsByUserIdAndTimeSlotAndStatusNot(userId: UUID, timeSlot: TimeSlot, status: ReservationStatus): Boolean
    fun findAllByUserId(userId: UUID): List<Reservation>
    fun countByTimeSlot_Date(date: LocalDate): Int
    fun findAllWithFilters(date: LocalDate?, time: LocalTime?, status: String?, pageable: Pageable): Page<Reservation>
    fun countByUserIdAndStatusAndCancelledAtAfter(userId: UUID, status: ReservationStatus, dateTime: LocalDateTime): Int
    fun findAllByStatusAndReleaseAtBefore(status: ReservationStatus, dateTime: LocalDateTime): List<Reservation>
    fun findAllByStatusAndCreatedAtBefore(status: ReservationStatus, createdAt: LocalDateTime): List<Reservation>
    fun findAllByStatus(status: ReservationStatus): List<Reservation>
    fun findByTimeSlotIdAndStatusOrderByCreatedAt(timeSlotId: UUID, status: ReservationStatus): List<Reservation>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Reservation r WHERE r.id = :id")
    fun findByIdWithLock(@Param("id") id: UUID): Reservation?
}