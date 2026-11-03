package com.shop.backend.einvoice.exception;

public class InvoiceBalanceExceededException extends RuntimeException {
    public InvoiceBalanceExceededException(String message) {
        super(message);
    }
}
