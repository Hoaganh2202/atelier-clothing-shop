package com.shop.backend.inventory;

import com.shop.backend.common.exception.BadRequestException;

public enum InventoryStockStatus {
    ALL,
    OUT_OF_STOCK,
    LOW_STOCK,
    IN_STOCK;

    public static InventoryStockStatus fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return ALL;
        }
        try {
            return InventoryStockStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Trạng thái tồn kho không hợp lệ: " + value);
        }
    }

    public static InventoryStockStatus calculateStatus(int stock, int threshold) {
        if (stock == 0) {
            return OUT_OF_STOCK;
        }
        if (stock < threshold) {
            return LOW_STOCK;
        }
        return IN_STOCK;
    }
}
