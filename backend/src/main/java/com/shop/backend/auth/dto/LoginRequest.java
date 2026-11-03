package com.shop.backend.auth.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Email hoặc số điện thoại không được để trống")
        @JsonAlias({"identifier", "phone"}) // TODO: support identifier/phone alias until unified
        String email,

        @NotBlank(message = "Mật khẩu không được để trống")
        String password
) {
    public LoginRequest {
        email = email != null ? email.trim() : null;
    }
}
