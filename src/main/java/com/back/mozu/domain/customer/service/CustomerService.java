package com.back.mozu.domain.customer.service;

import com.back.mozu.domain.customer.entity.Customer;
import com.back.mozu.domain.customer.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerService {
    private final CustomerRepository customerRepository;

    // Rq나 다른 서비스에서 가장 많이 쓸 "이메일로 찾기"
    public Optional<Customer> findByEmail(String email) {
        return customerRepository.findByEmail(email);
    }

    // ID로 찾기
    public Optional<Customer> findById(UUID id) {
        return customerRepository.findById(id);
    }

    // 존재 여부 확인 (나중에 회원가입 로직 등에서 활용)
    public boolean existsByEmail(String email) {
        return customerRepository.existsByEmail(email);
    }
}