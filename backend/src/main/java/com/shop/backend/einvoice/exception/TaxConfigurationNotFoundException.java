package com.shop.backend.einvoice.exception;

public class TaxConfigurationNotFoundException extends RuntimeException {
    public TaxConfigurationNotFoundException(String message) {
        super(message);
    }
}
