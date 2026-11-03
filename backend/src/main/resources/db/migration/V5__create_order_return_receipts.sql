-- -----------------------------------------------------------------------------
-- V5: Create order_return_receipts table for return receipt registry and idempotency
-- -----------------------------------------------------------------------------

CREATE TABLE order_return_receipts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    receipt_number VARCHAR(50) NOT NULL,
    order_id BIGINT NOT NULL,
    actor_user_id BIGINT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    total_received_quantity INT NOT NULL,
    restocked_quantity INT NOT NULL,
    discarded_quantity INT NOT NULL,
    note VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_return_receipt_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT,
    CONSTRAINT fk_return_receipt_actor FOREIGN KEY (actor_user_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT uq_order_return_receipt UNIQUE (order_id, receipt_number),

    CONSTRAINT chk_return_receipt_total_qty CHECK (total_received_quantity > 0),
    CONSTRAINT chk_return_receipt_restocked_qty CHECK (restocked_quantity >= 0),
    CONSTRAINT chk_return_receipt_discarded_qty CHECK (discarded_quantity >= 0),
    CONSTRAINT chk_return_receipt_sum_qty CHECK (total_received_quantity = restocked_quantity + discarded_quantity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_return_receipt_order ON order_return_receipts(order_id);
