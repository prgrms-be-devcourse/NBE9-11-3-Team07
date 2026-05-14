package com.back.mozu.global.response

import com.back.mozu.domain.customer.entity.Customer
import com.back.mozu.domain.customer.service.CustomerService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.context.annotation.RequestScope
import java.util.UUID

@Component
@RequestScope
class Rq(
    private val customerService: CustomerService,
    private val req: HttpServletRequest,
    private val resp: HttpServletResponse,
) {
    private var actor: Customer? = null
    private var isActorLoaded = false

    fun getActor(): Customer? {
        if (isActorLoaded) {
            return actor
        }

        val authentication = SecurityContextHolder.getContext().authentication

        if (authentication == null || !authentication.isAuthenticated || authentication.principal == "anonymousUser") {
            isActorLoaded = true
            return null
        }

        val identifier = authentication.name

        actor = try {
            customerService.findById(UUID.fromString(identifier)).orElse(null)
        } catch (_: IllegalArgumentException) {
            customerService.findByEmail(identifier).orElse(null)
        }

        isActorLoaded = true
        return actor
    }

    fun isLogin(): Boolean = getActor() != null

    fun isLogout(): Boolean = !isLogin()

    fun isAdmin(): Boolean = !isLogout() && getActor()?.role == "ADMIN"

    fun getHeader(name: String): String? = req.getHeader(name)
}