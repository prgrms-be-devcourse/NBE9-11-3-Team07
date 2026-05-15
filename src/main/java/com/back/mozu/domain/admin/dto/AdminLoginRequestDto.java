package com.back.mozu.domain.admin.dto;


import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class AdminLoginRequestDto {
    @NotBlank
    private String loginId;
    @NotBlank
    private String password;
}
