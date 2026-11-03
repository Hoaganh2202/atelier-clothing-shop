package com.shop.backend.shipping.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShippingSummaryResponse {

    private long readyToShipCount;
    private long deliveringCount;
    private long deliveredTodayCount;
}
