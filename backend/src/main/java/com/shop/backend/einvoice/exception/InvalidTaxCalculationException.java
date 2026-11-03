package com.shop.backend.einvoice.exception;

public class InvalidTaxCalculationException extends RuntimeException {
    public InvalidTaxCalculationException(String message) {
        super(message);
    }
}
