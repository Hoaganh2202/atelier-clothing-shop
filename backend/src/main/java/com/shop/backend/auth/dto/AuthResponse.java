package com.shop.backend.auth.dto;

public record AuthResponse(
        String accessToken,
        UserSummaryResponse user
) {}
