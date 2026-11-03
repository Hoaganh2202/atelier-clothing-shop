package com.shop.backend.inventory;

import com.shop.backend.inventory.dto.OrderReturnReceiptItem;
import com.shop.backend.inventory.dto.OrderReturnReceiptResult;
import com.shop.backend.inventory.dto.OrderStockItem;
import com.shop.backend.inventory.dto.OrderStockRestoreResult;
import java.util.List;

public interface InventoryStockService {

    void recordCheckoutDeduction(Long orderId, Long actorUserId, List<OrderStockItem> items);

    OrderStockRestoreResult restoreStockForCancelledOrder(
            Long orderId,
            Long actorUserId,
            String reason,
            List<OrderStockItem> items
    );

    /**
     * Checks whether an order has any CANCEL_RESTORE inventory movement transactions recorded.
     *
     * @param orderId the order ID
     * @return true if CANCEL_RESTORE transactions exist for this order, false otherwise
     */
    boolean hasCancellationRestoreForOrder(Long orderId);

    /**
     * Processes physical return receipt and restores inventory stock when applicable.
     * <p>
     * Caller contract:
     * - The caller (Order module) MUST acquire a pessimistic write lock on the target Order
     *   (e.g., via findByIdForUpdate) before invoking this method to ensure atomic serialization.
     * - The caller MUST validate that the order is in an eligible return state
     *   (REFUNDED or CANCELLED from SHIPPING) prior to calling this method.
     * - This method participates in the caller's active transaction (Propagation.REQUIRED).
     *
     * @param orderId the order ID (must not be null)
     * @param actorUserId the authenticated user ID performing the receipt (optional)
     * @param receiptNumber the business receipt identifier (1-40 chars, alphanumeric + '-' + '_')
     * @param note optional receipt note (max 500 chars)
     * @param items the list of returned items with quantities and conditions
     * @return the result of the processed return receipt
     */
    OrderReturnReceipt getReturnReceiptEntity(Long id);

    OrderReturnReceiptResult processReturnReceipt(
            Long orderId,
            Long actorUserId,
            String receiptNumber,
            String note,
            List<OrderReturnReceiptItem> items
    );
}
