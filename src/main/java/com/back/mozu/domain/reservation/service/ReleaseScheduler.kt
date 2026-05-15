package com.back.mozu.domain.reservation.service

import com.back.mozu.domain.reservation.entity.Reservation

interface ReleaseScheduler {
    fun schedule(reservation: Reservation)
}
