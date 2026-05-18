package com.back.mozu.domain.customer.repository

import com.back.mozu.domain.customer.entity.Customer
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CustomerRepository : JpaRepository<Customer, UUID> {
    fun findByEmail(email: String): Customer?

    fun findByEmailOrNull(email: String): Customer? = findByEmail(email)

    fun findByProviderId(providerId: String): Customer?

    fun existsByEmail(email: String): Boolean
}