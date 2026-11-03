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
public class CalculatedInvoiceResult {
    private PricingBasis pricingBasis;
    private LocalDateTime taxPoint;
    private List<CalculatedInvoiceLineResult> lines;
    private CalculatedInvoiceLineResult shippingLine;
    private BigDecimal totalMerchandiseBeforeTax;
    private BigDecimal totalMerchandiseDiscount;
    private BigDecimal totalShippingBeforeTax;
    private BigDecimal totalShippingDiscount;
    private BigDecimal totalTaxableAmount;
    private BigDecimal totalTaxAmount;
    private BigDecimal totalAfterTax;
}
