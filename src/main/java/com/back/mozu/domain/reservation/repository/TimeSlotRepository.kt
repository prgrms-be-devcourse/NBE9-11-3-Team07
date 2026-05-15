package com.back.mozu.domain.reservation.repository

import com.back.mozu.domain.reservation.entity.TimeSlot
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

interface TimeSlotRepository : JpaRepository<TimeSlot, UUID> {
    fun findByDateAndTime(date: LocalDate, time: LocalTime): TimeSlot?

    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    @Query("SELECT t FROM TimeSlot t WHERE t.id = :id")
    fun findByIdWithLock(@Param("id") id: UUID): TimeSlot?
}
