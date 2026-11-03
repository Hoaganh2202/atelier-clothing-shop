package com.shop.backend.auth.dto;

import com.shop.backend.user.Gender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record UpdateProfileRequest(
        @NotBlank(message = "Họ tên không được để trống")
        @Size(max = 150, message = "Họ tên không được vượt quá 150 ký tự")
        String name,

        @Pattern(regexp = "^(0[0-9]{9})?$", message = "Số điện thoại không hợp lệ")
        String phone,

        Gender gender,

        @PastOrPresent(message = "Ngày sinh không được nằm trong tương lai")
        LocalDate birthDate
) {}
