package com.shop.backend.common.exception;

public class OutOfStockException extends BadRequestException {

    public OutOfStockException(String message) {
        super(message);
    }
}
