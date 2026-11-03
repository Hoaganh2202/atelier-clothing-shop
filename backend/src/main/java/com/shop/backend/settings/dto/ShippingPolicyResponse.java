package com.shop.backend.settings.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShippingPolicyResponse {
    private Long shippingFee;
    private Long freeShippingThreshold;
}
