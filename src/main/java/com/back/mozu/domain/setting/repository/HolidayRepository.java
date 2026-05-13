package com.back.mozu.domain.setting.repository;

import com.back.mozu.domain.setting.entity.Holiday;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HolidayRepository extends JpaRepository<Holiday, LocalDate> {
    List<Holiday> findByDateBetweenOrderByDateAsc(LocalDate startDate, LocalDate endDate);
}