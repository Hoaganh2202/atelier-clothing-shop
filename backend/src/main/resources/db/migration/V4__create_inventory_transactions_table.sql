-- -----------------------------------------------------------------------------
-- V4: Create inventory_transactions table for stock movements audit and restoration
-- Note: 'manual_adjust', 'return_restock', and 'return_discard' are reserved for future phases.
-- -----------------------------------------------------------------------------

CREATE TABLE inventory_transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    variant_id BIGINT NOT NULL,
    order_id BIGINT NULL,
    order_detail_id BIGINT NULL,
    movement_type VARCHAR(30) NOT NULL,
    quantity INT NOT NULL,
    stock_before INT NOT NULL,
    stock_after INT NOT NULL,
    actor_user_id BIGINT NULL,
    note VARCHAR(500) NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_inv_tx_variant FOREIGN KEY (variant_id) REFERENCES product_variants(id) ON DELETE RESTRICT,
    CONSTRAINT fk_inv_tx_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT,
    CONSTRAINT fk_inv_tx_order_detail FOREIGN KEY (order_detail_id) REFERENCES order_details(id) ON DELETE RESTRICT,
    CONSTRAINT fk_inv_tx_actor FOREIGN KEY (actor_user_id) REFERENCES users(id) ON DELETE SET NULL,

    CONSTRAINT chk_inv_tx_movement_type CHECK (movement_type IN (
        'checkout_deduct',
        'cancel_restore',
        'manual_adjust',
        'return_restock',
        'return_discard'
    )),
    CONSTRAINT chk_inv_tx_quantity CHECK (quantity > 0),
    CONSTRAINT chk_inv_tx_stock_before CHECK (stock_before >= 0),
    CONSTRAINT chk_inv_tx_stock_after CHECK (stock_after >= 0),
    CONSTRAINT uq_inv_tx_idempotency UNIQUE (idempotency_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_inv_tx_variant_created ON inventory_transactions(variant_id, created_at);
CREATE INDEX idx_inv_tx_order ON inventory_transactions(order_id);
CREATE INDEX idx_inv_tx_order_movement ON inventory_transactions(order_id, movement_type);
