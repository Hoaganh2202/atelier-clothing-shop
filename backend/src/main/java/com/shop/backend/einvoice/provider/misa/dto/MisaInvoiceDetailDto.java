package com.shop.backend.einvoice.provider.misa.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MisaInvoiceDetailDto {

    @JsonProperty("LineNumber")
    private Integer lineNumber;

    @JsonProperty("ItemCode")
    private String itemCode;

    @JsonProperty("ItemName")
    private String itemName;

    @JsonProperty("UnitName")
    private String unitName;

    @JsonProperty("Quantity")
    private BigDecimal quantity;

    @JsonProperty("UnitPrice")
    private BigDecimal unitPrice;

    @JsonProperty("AmountOC")
    private BigDecimal amountOC;

    @JsonProperty("DiscountAmountOC")
    private BigDecimal discountAmountOC;

    @JsonProperty("VATRateName")
    private String vatRateName; // "8%", "10%", "5%", "0%", "KCT", "KKTT"

    @JsonProperty("VATAmountOC")
    private BigDecimal vatAmountOC;

    @JsonProperty("IsPromotion")
    private Boolean isPromotion;
}
