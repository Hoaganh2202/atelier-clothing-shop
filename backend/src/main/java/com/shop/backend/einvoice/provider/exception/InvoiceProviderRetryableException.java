package com.shop.backend.einvoice.provider.exception;

import com.shop.backend.einvoice.model.AttemptOutcome;

public class InvoiceProviderRetryableException extends InvoiceProviderException {

    public InvoiceProviderRetryableException(
            String providerName,
            String errorCode,
            String message,
            String rawResponse,
            Throwable cause) {
        super(providerName, errorCode, message, AttemptOutcome.FAILED, true, rawResponse, cause);
    }

    public InvoiceProviderRetryableException(
            String providerName,
            String errorCode,
            String message,
            String rawResponse) {
        super(providerName, errorCode, message, AttemptOutcome.FAILED, true, rawResponse);
    }
}
