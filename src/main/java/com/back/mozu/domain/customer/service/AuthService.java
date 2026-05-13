package com.back.mozu.domain.customer.service;

import com.back.mozu.domain.customer.dto.CustomerDto;
import com.back.mozu.domain.customer.entity.Customer;
import com.back.mozu.domain.customer.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final CustomerRepository customerRepository;

    public CustomerDto.MeResponse getMe(String email) {
        Customer customer = customerRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다."));

        return new CustomerDto.MeResponse(
                customer.getId().toString(),
                customer.getEmail(),
                customer.getEmail(),
                customer.getRole()
        );
    }
}
