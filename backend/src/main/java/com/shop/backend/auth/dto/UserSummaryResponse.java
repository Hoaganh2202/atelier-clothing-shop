package com.shop.backend.auth.dto;

import com.shop.backend.user.Gender;
import java.time.LocalDate;

public record UserSummaryResponse(
        Long id,
        String name,
        String email,
        String phone,
        Gender gender,
        LocalDate birthDate,
        String role
) {}
