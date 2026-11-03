package com.shop.backend.einvoice.provider.exception;

import com.shop.backend.einvoice.model.AttemptOutcome;

public class InvoiceProviderException extends RuntimeException {

    private final String providerName;
    private final String errorCode;
    private final AttemptOutcome attemptOutcome;
    private final boolean retryable;
    private final String rawResponse;

    public InvoiceProviderException(
            String providerName,
            String errorCode,
            String message,
            AttemptOutcome attemptOutcome,
            boolean retryable,
            String rawResponse,
            Throwable cause) {
        super(message, cause);
        this.providerName = providerName;
        this.errorCode = errorCode;
        this.attemptOutcome = attemptOutcome;
        this.retryable = retryable;
        this.rawResponse = rawResponse;
    }

    public InvoiceProviderException(
            String providerName,
            String errorCode,
            String message,
            AttemptOutcome attemptOutcome,
            boolean retryable,
            String rawResponse) {
        this(providerName, errorCode, message, attemptOutcome, retryable, rawResponse, null);
    }

    public String getProviderName() {
        return providerName;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public AttemptOutcome getAttemptOutcome() {
        return attemptOutcome;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public String getRawResponse() {
        return rawResponse;
    }
}
