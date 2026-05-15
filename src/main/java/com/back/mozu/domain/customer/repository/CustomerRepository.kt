package com.back.mozu.domain.customer.repository

import com.back.mozu.domain.customer.entity.Customer
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface CustomerRepository : JpaRepository<Customer, UUID> {
    fun findByEmail(email: String): Optional<Customer>
    fun findByProviderId(providerId: String): Optional<Customer>
    fun existsByEmail(email: String): Boolean
}
