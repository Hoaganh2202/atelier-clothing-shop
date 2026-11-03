package com.shop.backend.einvoice.provider.dto;

import com.shop.backend.einvoice.model.InvoiceType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ProviderIssueRequest {
    Long invoiceId;
    String refId;
    InvoiceType invoiceType;
    String templateCode;
    String invoiceSeries;
    LocalDateTime invoiceDate;
    String paymentMethod;

    String sellerTaxCode;
    String sellerLegalName;
    String sellerAddress;

    String buyerLegalName;
    String buyerTaxCode;
    String buyerAddress;
    String buyerEmail;
    String buyerPhone;

    BigDecimal totalGrossAmount;
    BigDecimal totalNetAmount;
    BigDecimal totalVatAmount;
    BigDecimal totalDiscountAmount;

    List<ProviderInvoiceItemDto> items;

    // Reference fields for ADJUSTMENT and REPLACEMENT
    String originalInvoiceNo;
    String originalInvoiceSeries;
    String originalTemplateCode;
    LocalDateTime originalInvoiceDate;
    String adjustmentReason;
}
