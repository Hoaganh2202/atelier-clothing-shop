package com.shop.backend.einvoice.provider.dto;

import com.shop.backend.einvoice.model.AttemptOutcome;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ProviderInquiryResult {
    boolean found;
    String refId;
    String transactionId;
    String invoiceNo;
    String invoiceSeries;
    String templateCode;
    String status;
    LocalDateTime invoiceDate;
    String taxAuthorityCode;
    String lookupCode;
    AttemptOutcome attemptOutcome;
    String rawResponse;
    boolean isIssued;
    boolean isRejected;
}
