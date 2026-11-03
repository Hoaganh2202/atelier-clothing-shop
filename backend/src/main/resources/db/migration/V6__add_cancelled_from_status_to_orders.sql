-- -----------------------------------------------------------------------------
-- V6: Add cancelled_from_status column to orders table to track cancellation provenance
-- -----------------------------------------------------------------------------

ALTER TABLE orders
    ADD COLUMN cancelled_from_status VARCHAR(30) NULL AFTER status,
    ADD CONSTRAINT chk_orders_cancelled_from_status
        CHECK (cancelled_from_status IS NULL OR cancelled_from_status IN ('PENDING', 'CONFIRMED', 'SHIPPING'));
