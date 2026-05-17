package com.back.mozu.domain.customer.service

import com.back.mozu.domain.customer.entity.Customer
import com.back.mozu.domain.customer.repository.CustomerRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Optional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class CustomerService(
    private val customerRepository: CustomerRepository,
) {
    // Rq나 다른 서비스에서 가장 많이 쓸 "이메일로 찾기"
    fun findByEmail(email: String): Optional<Customer> {
        return customerRepository.findByEmail(email)
    }

    // ID로 찾기
    fun findById(id: UUID): Customer? {
        return customerRepository.findByIdOrNull(id)
    }

    // 존재 여부 확인 (나중에 회원가입 로직 등에서 활용)
    fun existsByEmail(email: String): Boolean {
        return customerRepository.existsByEmail(email)
    }

    fun findByIdOrNull(id: UUID): Customer? {
        return customerRepository.findByIdOrNull(id)
    }
}
