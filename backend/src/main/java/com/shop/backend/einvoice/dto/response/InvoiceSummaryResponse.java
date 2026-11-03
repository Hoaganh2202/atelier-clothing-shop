package com.shop.backend.einvoice.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.shop.backend.einvoice.model.AdjustmentType;
import com.shop.backend.einvoice.model.DocumentStatus;
import com.shop.backend.einvoice.model.InvoiceType;
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
public class InvoiceSummaryResponse {
    private Long id;
    private Long orderId;
    private String orderNumber;
    private InvoiceType invoiceType;
    private AdjustmentType adjustmentType;
    private DocumentStatus documentStatus;
    private String invoiceSeries;
    private String invoiceNumber;
    private String taxAuthorityCode;
    private LocalDateTime taxPointDate;
    private LocalDateTime officiallyIssuedAt;
    private Long totalTaxableAmount;
    private Long totalTaxAmount;
    private Long totalAfterTax;
    private String buyerName;

    @JsonProperty("isDemo")
    private Boolean isDemo;

    private Boolean demoMode;

    private LocalDateTime createdAt;
}
