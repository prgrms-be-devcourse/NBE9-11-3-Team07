package com.back.mozu.domain.customer.dto

import com.back.mozu.domain.customer.entity.Customer

class CustomerDto {
    data class MeResponse(
        val userId: String,
        val name: String,
        val email: String,
        val role: String,
    ) {
        constructor(actor: Customer) : this(
            userId = requireNotNull(actor.id).toString(),
            name = actor.name,
            email = actor.email,
            role = actor.role,
        )
    }
}