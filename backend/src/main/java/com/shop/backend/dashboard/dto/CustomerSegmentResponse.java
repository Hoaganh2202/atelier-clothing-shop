package com.shop.backend.dashboard.dto;

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
public class CustomerSegmentResponse {
    private String id;
    private String name;
    private Long count;
    private Double percentage;
    private String color;
    private String description;
}
