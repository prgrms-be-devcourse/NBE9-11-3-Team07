package com.back.mozu.domain.setting.repository

import com.back.mozu.domain.setting.entity.RestaurantSettings
import org.springframework.data.jpa.repository.JpaRepository

interface RestaurantSettingRepository : JpaRepository<RestaurantSettings, Int>
