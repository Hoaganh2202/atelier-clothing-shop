package com.shop.backend.einvoice.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.shop.backend.einvoice.model.AdjustmentType;
import com.shop.backend.einvoice.model.DocumentStatus;
import com.shop.backend.einvoice.model.InvoiceType;
import com.shop.backend.einvoice.model.PricingBasis;
import java.time.LocalDateTime;
import java.util.List;
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
public class InvoiceDetailResponse {
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
    private PricingBasis pricingBasis;

    private Long totalMerchandiseBeforeTax;
    private Long totalMerchandiseDiscount;
    private Long totalShippingBeforeTax;
    private Long totalShippingDiscount;
    private Long totalTaxableAmount;
    private Long totalTaxAmount;
    private Long totalAfterTax;

    private String storageProvider;

    @JsonProperty("isDemo")
    private Boolean isDemo;

    private Boolean demoMode;
    private String demoNotice;

    @JsonProperty("isReplaced")
    private Boolean isReplaced;

    private Long originalInvoiceId;
    private Long replacedByInvoiceId;
    private Long replacingInvoiceId;

    private DocumentStatus replacementStatus;
    private String replacementNotice;

    private InvoiceBuyerResponse buyer;
    private InvoiceSellerResponse seller;
    private List<InvoiceItemResponse> items;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
