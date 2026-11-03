package com.shop.backend.einvoice.provider.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ProviderCancelRequest {
    String refId;
    String transactionId;
    String invoiceNo;
    String invoiceSeries;
    String reason;
    LocalDateTime cancelledAt;
}
