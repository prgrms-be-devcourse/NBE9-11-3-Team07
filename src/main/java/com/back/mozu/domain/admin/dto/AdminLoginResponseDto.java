package com.back.mozu.domain.admin.dto;


import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class AdminLoginResponseDto {
    private String accessToken;
    private String refrestToken;
    private AdminUserDto adminUser;

    @Getter
    @Builder
    public static class AdminUserDto{
        private String adminId;
        private String loginId;
        private String name;
    }
}
