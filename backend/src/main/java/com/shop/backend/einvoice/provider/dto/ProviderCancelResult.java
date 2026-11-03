package com.shop.backend.einvoice.provider.dto;

import com.shop.backend.einvoice.model.AttemptOutcome;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ProviderCancelResult {
    boolean success;
    String message;
    AttemptOutcome attemptOutcome;
    String rawResponse;
    boolean retryable;
}
