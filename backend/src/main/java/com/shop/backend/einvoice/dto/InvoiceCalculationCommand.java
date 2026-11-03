package com.shop.backend.einvoice.dto;

import com.shop.backend.einvoice.model.PricingBasis;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
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
public class InvoiceCalculationCommand {
    private Long orderId;
    private Long fulfillmentEventId;
    private LocalDateTime taxPoint;
    private PricingBasis pricingBasis;
    private List<InvoiceLineCalculationCommand> items;
    private BigDecimal merchandiseVoucherAmount;
    private BigDecimal shippingFee;
    private BigDecimal shippingPromotionAmount;
    private String shippingTaxCategory;
}
