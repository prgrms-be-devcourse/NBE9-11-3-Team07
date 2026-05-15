package com.back.mozu.domain.reservation.repository

import com.back.mozu.domain.reservation.entity.Reservation
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.time.LocalDate
import java.time.LocalTime

interface ReservationRepositoryCustom {
    fun findAllWithFilters(
        date: LocalDate?,
        time: LocalTime?,
        status: String?,
        pageable: Pageable
    ): Page<Reservation>
}
