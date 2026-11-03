package com.shop.backend.einvoice.provider.exception;

import com.shop.backend.einvoice.model.AttemptOutcome;

public class InvoiceProviderAuthException extends InvoiceProviderException {

    public InvoiceProviderAuthException(
            String providerName,
            String errorCode,
            String message,
            String rawResponse,
            Throwable cause) {
        super(providerName, errorCode, message, AttemptOutcome.FAILED, false, rawResponse, cause);
    }

    public InvoiceProviderAuthException(
            String providerName,
            String errorCode,
            String message,
            String rawResponse) {
        super(providerName, errorCode, message, AttemptOutcome.FAILED, false, rawResponse);
    }
}
