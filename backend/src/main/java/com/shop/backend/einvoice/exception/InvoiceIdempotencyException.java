package com.shop.backend.einvoice.exception;

public class InvoiceIdempotencyException extends RuntimeException {
    public InvoiceIdempotencyException(String message) {
        super(message);
    }
}
