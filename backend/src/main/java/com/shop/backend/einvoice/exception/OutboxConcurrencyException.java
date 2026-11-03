package com.shop.backend.einvoice.exception;

public class OutboxConcurrencyException extends RuntimeException {
    public OutboxConcurrencyException(String message) {
        super(message);
    }
}
