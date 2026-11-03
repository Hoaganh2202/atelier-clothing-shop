package com.shop.backend.einvoice.provider.exception;

import com.shop.backend.einvoice.model.AttemptOutcome;

public class InvoiceProviderFatalException extends InvoiceProviderException {

    public InvoiceProviderFatalException(
            String providerName,
            String errorCode,
            String message,
            String rawResponse,
            Throwable cause) {
        super(providerName, errorCode, message, AttemptOutcome.FAILED, false, rawResponse, cause);
    }

    public InvoiceProviderFatalException(
            String providerName,
            String errorCode,
            String message,
            String rawResponse) {
        super(providerName, errorCode, message, AttemptOutcome.FAILED, false, rawResponse);
    }
}
