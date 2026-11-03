package com.shop.backend.einvoice.provider.dto;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ProviderInvoiceItemDto {
    Long invoiceItemId;
    Integer lineNumber;
    String itemCode;
    String itemName;
    String unitName;
    BigDecimal quantity;
    BigDecimal unitPrice;
    BigDecimal discountAmount;
    BigDecimal netAmount;
    BigDecimal vatRate;
    BigDecimal vatAmount;
    BigDecimal grossAmount;
    boolean isNegative;
}
