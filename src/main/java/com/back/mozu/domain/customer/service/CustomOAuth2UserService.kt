package com.back.mozu.domain.customer.service

import com.back.mozu.domain.customer.entity.Customer
import com.back.mozu.domain.customer.repository.CustomerRepository
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.core.user.DefaultOAuth2User
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Service

@Service
class CustomOAuth2UserService(
    private val customerRepository: CustomerRepository,
) : DefaultOAuth2UserService() {

    override fun loadUser(userRequest: OAuth2UserRequest): OAuth2User {
        // Spring Security가 구글에서 가져온 유저 정보 받기
        val oAuth2User = super.loadUser(userRequest)

        // 구글이 준 유저 정보에서 필요한 값 꺼내기
        val attributes = oAuth2User.attributes
        val providerId = attributes["sub"] as String
        val email = attributes["email"] as String
        val name = attributes["name"] as String

        val existingCustomer = customerRepository.findByProviderId(providerId)

        val customer: Customer
        val isNewUser: Boolean

        if (existingCustomer == null) {
            customer = customerRepository.save(
                Customer(
                    email = email,
                    provider = "google",
                    providerId = providerId,
                    role = "USER",
                    password = null,
                    name = name,
                ),
            )
            isNewUser = true
        } else {
            customer = existingCustomer
            customer.updateFromOAuth(name, email)
            customerRepository.save(customer)
            isNewUser = false
        }

        // OAuth2SuccessHandler에서 userId, role, isNewUser를 쓸 수 있도록 attributes에 추가해서 반환
        val newAttributes = mapOf(
            "userId" to requireNotNull(customer.id),
            "role" to customer.role,
            "isNewUser" to isNewUser,
        )

        return DefaultOAuth2User(
            oAuth2User.authorities,
            newAttributes,
            "userId", // Map에서 primary key 역할 하는 키 이름
        )
    }
}

