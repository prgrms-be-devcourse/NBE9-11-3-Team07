package com.back.mozu.domain.setting.service

import com.back.mozu.domain.setting.dto.SettingDto.GetSettingResponse
import com.back.mozu.domain.setting.repository.SettingRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
// @RequiredArgsConstructor 제거 → 코틀린 생성자 주입으로 대체
class SettingService(
    private val settingRepository: SettingRepository
) {
    // Java 프로퍼티(getter) → 코틀린 일반 함수로 변환
    fun getSetting(): GetSettingResponse {
        // Optional.orElseThrow() → findByIdOrNull() ?: throw 로 대체
        val setting = settingRepository.findByIdOrNull(1)
            ?: throw IllegalArgumentException("설정 정보를 찾을 수 없습니다.")

        return GetSettingResponse(
            setting.totalTables,
            setting.openingTime,
            setting.closingTime
        )
    }
}