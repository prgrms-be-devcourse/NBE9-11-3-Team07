package com.back.mozu.domain.customer.service

import com.back.mozu.domain.customer.dto.CustomerDto.MeResponse
import com.back.mozu.domain.customer.repository.CustomerRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class AuthService(
    private val customerRepository: CustomerRepository,
) {
    fun getMe(email: String): MeResponse {
        val customer = customerRepository.findByEmail(email)
            .orElseThrow { IllegalArgumentException("유저를 찾을 수 없습니다.") }

        return MeResponse(customer)
    }
}
