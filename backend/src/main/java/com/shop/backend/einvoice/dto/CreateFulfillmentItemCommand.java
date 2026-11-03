package com.shop.backend.einvoice.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateFulfillmentItemCommand {
    private Long orderDetailId;
    private Integer deliveredQuantity;
    private BigDecimal unitPriceSnapshot;
    private BigDecimal discountAllocated;
}
