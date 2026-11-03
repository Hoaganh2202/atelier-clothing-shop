-- Migration V3: Add idempotency_key to orders for duplicate checkout prevention
ALTER TABLE orders
    ADD COLUMN idempotency_key VARCHAR(100) NULL,
    ADD CONSTRAINT uq_orders_user_idempotency UNIQUE (user_id, idempotency_key);
