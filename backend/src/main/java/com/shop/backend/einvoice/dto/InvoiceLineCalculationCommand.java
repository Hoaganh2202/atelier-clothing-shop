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
public class InvoiceLineCalculationCommand {
    private Long orderDetailId;
    private Long fulfillmentEventItemId;
    private String itemName;
    private String unit;
    private Integer quantity;
    private BigDecimal unitPrice;
    private String taxCategory;
    @Builder.Default
    private Boolean isDiscountable = true;
}
