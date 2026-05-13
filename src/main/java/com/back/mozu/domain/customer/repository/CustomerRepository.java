package com.back.mozu.domain.customer.repository;

import com.back.mozu.domain.customer.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    Optional<Customer> findByEmail(String email);
    Optional<Customer> findByProviderId(String providerId);
    boolean existsByEmail(String email);
}
