package com.shop.backend.inventory;

public enum InventoryMovementType {
    CHECKOUT_DEDUCT,
    CANCEL_RESTORE,
    MANUAL_ADJUST,
    RETURN_RESTOCK,
    RETURN_DISCARD
}
