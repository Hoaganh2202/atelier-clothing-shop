package com.shop.backend.einvoice.provider.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ProviderInquiryRequest {
    Long invoiceId;
    String refId;
    String transactionId;
    String invoiceNo;
    String invoiceSeries;
}
