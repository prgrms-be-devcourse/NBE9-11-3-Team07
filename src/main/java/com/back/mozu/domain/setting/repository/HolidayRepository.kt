package com.back.mozu.domain.setting.repository

import com.back.mozu.domain.setting.entity.Holiday
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface HolidayRepository : JpaRepository<Holiday, LocalDate> {
    fun findByDateBetweenOrderByDateAsc(startDate: LocalDate, endDate: LocalDate): List<Holiday>
}