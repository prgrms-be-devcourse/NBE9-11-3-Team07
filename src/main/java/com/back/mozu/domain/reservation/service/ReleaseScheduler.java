package com.back.mozu.domain.reservation.service;

import com.back.mozu.domain.reservation.entity.Reservation;

public interface ReleaseScheduler {
    void schedule(Reservation reservation);
}
