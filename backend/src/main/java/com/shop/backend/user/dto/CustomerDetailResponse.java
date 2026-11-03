package com.shop.backend.user.dto;

import com.shop.backend.common.model.DataSourceType;
import com.shop.backend.user.Gender;
import com.shop.backend.user.UserStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerDetailResponse {
    private Long id;
    private String name;
    private String email;
    private String phone;
    private Gender gender;
    private LocalDate birthDate;
    private UserStatus status;
    private LocalDateTime lastLoginAt;
    private DataSourceType dataSource;
    private LocalDateTime createdAt;
}
