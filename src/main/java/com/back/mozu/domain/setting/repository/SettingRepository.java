package com.back.mozu.domain.setting.repository;

import com.back.mozu.domain.setting.entity.RestaurantSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettingRepository extends JpaRepository<RestaurantSettings, Integer> {
}
