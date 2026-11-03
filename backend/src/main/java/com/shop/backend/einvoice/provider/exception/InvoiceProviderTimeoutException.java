package com.shop.backend.einvoice.provider.exception;

import com.shop.backend.einvoice.model.AttemptOutcome;

public class InvoiceProviderTimeoutException extends InvoiceProviderException {

    public InvoiceProviderTimeoutException(
            String providerName,
            String errorCode,
            String message,
            String rawResponse,
            Throwable cause) {
        super(providerName, errorCode, message, AttemptOutcome.TIMEOUT_UNKNOWN, true, rawResponse, cause);
    }

    public InvoiceProviderTimeoutException(
            String providerName,
            String message,
            Throwable cause) {
        super(providerName, "HTTP_TIMEOUT", message, AttemptOutcome.TIMEOUT_UNKNOWN, true, null, cause);
    }
}
