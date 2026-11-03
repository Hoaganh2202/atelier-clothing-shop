package com.shop.backend.einvoice.provider.dto;

import com.shop.backend.einvoice.model.AttemptOutcome;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ProviderIssueResult {
    boolean success;
    String transactionId;
    String invoiceNo;
    String invoiceSeries;
    String templateCode;
    LocalDateTime issuedAt;
    String taxAuthorityCode;
    String lookupCode;
    String lookupUrl;
    AttemptOutcome attemptOutcome;
    String errorCode;
    String errorMessage;
    String rawResponse;
    boolean retryable;
}
