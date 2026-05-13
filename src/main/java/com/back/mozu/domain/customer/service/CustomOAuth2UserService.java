package com.back.mozu.domain.customer.service;

import com.back.mozu.domain.customer.entity.Customer;
import com.back.mozu.domain.customer.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final CustomerRepository customerRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) {

        // Spring Security가 구글에서 가져온 유저 정보 받기
        OAuth2User oAuth2User = super.loadUser(userRequest);    // super : 부모 클래스를 가르키는 키워드

        // 구글이 준 유저 정보에서 필요한 값 꺼내기
        Map<String, Object> attributes = oAuth2User.getAttributes();
        String providerId = (String) attributes.get("sub");
        String email = (String) attributes.get("email");
        String name = (String) attributes.get("name");

        Optional<Customer> existingCustomer = customerRepository.findByProviderId(providerId);

        Customer customer;
        boolean isNewUser;

        if(existingCustomer.isEmpty()) {
            customer = customerRepository.save(
                    Customer.builder()
                            .email(email)
                            .provider("google")
                            .providerId(providerId)
                            .role("USER")
                            .name(name)
                            .build()
            );
            isNewUser = true;
        } else {
            customer = existingCustomer.get();
            customer.updateFromOAuth(name, email);
            customerRepository.save(customer);
            isNewUser = false;
        }

        // OAuth2SuccessHandler에서 userId, role, isNewUser를 쓸 수 있도록 attributes에 추가해서 반환
        Map<String, Object> newAttributes = Map.of(
                "userId", customer.getId(),
                "role", customer.getRole(),
                "isNewUser", isNewUser
        );

        return new DefaultOAuth2User(
                oAuth2User.getAuthorities(),
                newAttributes,
                "userId"   // Map에서 primary key 역할 하는 키 이름
        );
    }
}
