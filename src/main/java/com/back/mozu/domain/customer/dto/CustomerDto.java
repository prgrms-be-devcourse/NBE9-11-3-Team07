package com.back.mozu.domain.customer.dto;

import com.back.mozu.domain.customer.entity.Customer;

public class CustomerDto {

    public record MeResponse(
            String userId,
            String name,
            String email,
            String role
    ) {
        // Entity를 받아서 DTO로 변환하는 생성자
        public MeResponse(Customer actor) {
            this(
                    actor.getId().toString(), // UUID를 프론트엔드가 쓰기 편하게 String으로 변환
                    actor.getEmail(),         // 현재 엔티티에 name이 없으므로 이메일을 닉네임처럼 사용
                    actor.getEmail(),
                    actor.getRole()
            );
        }
    }
}