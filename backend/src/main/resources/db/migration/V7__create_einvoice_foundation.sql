-- -----------------------------------------------------------------------------
-- V7: Create electronic invoice foundation tables
-- -----------------------------------------------------------------------------

CREATE TABLE tax_configurations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tax_category VARCHAR(50) NOT NULL,
    tax_treatment VARCHAR(30) NOT NULL,
    tax_rate DECIMAL(5, 4) NULL,
    tax_code VARCHAR(20) NOT NULL,
    legal_reference VARCHAR(255) NOT NULL,
    pricing_basis VARCHAR(30) NOT NULL,
    effective_from DATETIME NOT NULL,
    effective_to DATETIME NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_tax_config_treatment CHECK (tax_treatment IN ('taxable', 'zero_rated', 'non_taxable', 'exempt')),
    CONSTRAINT chk_tax_config_pricing_basis CHECK (pricing_basis IN ('inclusive', 'exclusive')),
    CONSTRAINT chk_tax_config_rate CHECK (
        (tax_treatment IN ('non_taxable', 'exempt') AND tax_rate IS NULL) OR
        (tax_treatment IN ('taxable', 'zero_rated') AND tax_rate IS NOT NULL AND tax_rate >= 0)
    ),
    CONSTRAINT chk_tax_config_effective CHECK (effective_to > effective_from),
    CONSTRAINT uq_tax_category_effective UNIQUE (tax_category, pricing_basis, effective_from, effective_to)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_tax_config_lookup ON tax_configurations (tax_category, pricing_basis, is_active, effective_from, effective_to);

CREATE TABLE fulfillment_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    fulfillment_code VARCHAR(50) NOT NULL,
    carrier_name VARCHAR(100) NULL,
    tracking_number VARCHAR(100) NULL,
    status VARCHAR(30) NOT NULL,
    delivered_at DATETIME NOT NULL,
    proof_source VARCHAR(50) NOT NULL,
    proof_reference VARCHAR(255) NULL,
    note VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_fulfillment_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT,
    CONSTRAINT uq_fulfillment_code UNIQUE (fulfillment_code),
    CONSTRAINT chk_fulfillment_status CHECK (status IN ('delivered', 'in_transit', 'failed', 'returned')),
    CONSTRAINT chk_fulfillment_proof_source CHECK (proof_source IN ('carrier_webhook', 'manual_signature', 'admin_verified'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_fulfillment_order ON fulfillment_events(order_id);
CREATE INDEX idx_fulfillment_delivered ON fulfillment_events(delivered_at);

CREATE TABLE fulfillment_event_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    fulfillment_event_id BIGINT NOT NULL,
    order_detail_id BIGINT NOT NULL,
    delivered_quantity INT NOT NULL,
    unit_price_snapshot DECIMAL(12, 2) NOT NULL,
    discount_allocated DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_fei_fulfillment FOREIGN KEY (fulfillment_event_id) REFERENCES fulfillment_events(id) ON DELETE RESTRICT,
    CONSTRAINT fk_fei_order_detail FOREIGN KEY (order_detail_id) REFERENCES order_details(id) ON DELETE RESTRICT,
    CONSTRAINT uq_fulfillment_event_order_detail UNIQUE (fulfillment_event_id, order_detail_id),
    CONSTRAINT chk_fei_delivered_qty CHECK (delivered_quantity > 0),
    CONSTRAINT chk_fei_unit_price CHECK (unit_price_snapshot >= 0),
    CONSTRAINT chk_fei_discount CHECK (discount_allocated >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_fei_order_detail ON fulfillment_event_items(order_detail_id);

CREATE TABLE buyer_invoice_profiles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_type VARCHAR(30) NOT NULL,
    tax_code VARCHAR(30) NULL,
    company_name VARCHAR(255) NULL,
    buyer_name VARCHAR(150) NOT NULL,
    email VARCHAR(150) NOT NULL,
    address VARCHAR(300) NOT NULL,
    phone_number VARCHAR(30) NULL,
    is_immutable BOOLEAN NOT NULL DEFAULT FALSE,
    snapshotted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_buyer_customer_type CHECK (customer_type IN ('individual', 'enterprise')),
    CONSTRAINT chk_buyer_enterprise_info CHECK (customer_type <> 'enterprise' OR (tax_code IS NOT NULL AND company_name IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE seller_invoice_profiles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    seller_tax_code VARCHAR(30) NOT NULL,
    seller_legal_name VARCHAR(255) NOT NULL,
    seller_brand_name VARCHAR(150) NOT NULL,
    seller_address VARCHAR(300) NOT NULL,
    seller_phone VARCHAR(30) NOT NULL,
    seller_email VARCHAR(150) NULL,
    seller_bank_account VARCHAR(50) NULL,
    seller_bank_name VARCHAR(150) NULL,
    is_immutable BOOLEAN NOT NULL DEFAULT FALSE,
    snapshotted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE invoices (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    fulfillment_event_id BIGINT NULL,
    original_invoice_id BIGINT NULL,
    replaced_by_invoice_id BIGINT NULL,
    replacing_invoice_id BIGINT NULL,
    return_receipt_id BIGINT NULL,
    financial_settlement_reference VARCHAR(100) NULL,
    buyer_profile_id BIGINT NOT NULL,
    seller_profile_id BIGINT NOT NULL,
    invoice_type VARCHAR(30) NOT NULL,
    adjustment_type VARCHAR(30) NULL,
    document_status VARCHAR(30) NOT NULL,
    is_replaced BOOLEAN NOT NULL DEFAULT FALSE,
    invoice_series VARCHAR(20) NULL,
    invoice_number VARCHAR(30) NULL,
    tax_authority_code VARCHAR(100) NULL,
    tax_point_date DATETIME NOT NULL,
    officially_issued_at DATETIME NULL,
    pricing_basis VARCHAR(30) NOT NULL,
    total_merchandise_before_tax DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    total_merchandise_discount DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    total_shipping_before_tax DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    total_shipping_discount DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    total_taxable_amount DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    total_tax_amount DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    total_after_tax DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    xml_storage_key VARCHAR(255) NULL,
    pdf_storage_key VARCHAR(255) NULL,
    xml_sha256 VARCHAR(64) NULL,
    pdf_sha256 VARCHAR(64) NULL,
    storage_provider VARCHAR(50) NULL,
    file_size BIGINT NULL,
    retention_policy_ref VARCHAR(100) NULL,
    provider_transaction_id VARCHAR(100) NULL,
    idempotency_key VARCHAR(150) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_invoice_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT,
    CONSTRAINT fk_invoice_fulfillment FOREIGN KEY (fulfillment_event_id) REFERENCES fulfillment_events(id) ON DELETE RESTRICT,
    CONSTRAINT fk_invoice_original FOREIGN KEY (original_invoice_id) REFERENCES invoices(id) ON DELETE RESTRICT,
    CONSTRAINT fk_invoice_replaced_by FOREIGN KEY (replaced_by_invoice_id) REFERENCES invoices(id) ON DELETE RESTRICT,
    CONSTRAINT fk_invoice_replacing FOREIGN KEY (replacing_invoice_id) REFERENCES invoices(id) ON DELETE RESTRICT,
    CONSTRAINT fk_invoice_return_receipt FOREIGN KEY (return_receipt_id) REFERENCES order_return_receipts(id) ON DELETE RESTRICT,
    CONSTRAINT fk_invoice_buyer FOREIGN KEY (buyer_profile_id) REFERENCES buyer_invoice_profiles(id) ON DELETE RESTRICT,
    CONSTRAINT fk_invoice_seller FOREIGN KEY (seller_profile_id) REFERENCES seller_invoice_profiles(id) ON DELETE RESTRICT,

    CONSTRAINT chk_invoice_type CHECK (invoice_type IN ('original', 'adjustment', 'replacement')),
    CONSTRAINT chk_invoice_adj_type CHECK (adjustment_type IS NULL OR adjustment_type IN ('increase', 'decrease', 'information')),
    CONSTRAINT chk_invoice_doc_status CHECK (document_status IN ('draft', 'pending_issue', 'issuing', 'issuance_unknown', 'reconciling', 'issued', 'issue_failed')),
    CONSTRAINT chk_invoice_pricing_basis CHECK (pricing_basis IN ('inclusive', 'exclusive')),
    CONSTRAINT uq_invoice_idempotency UNIQUE (idempotency_key),
    CONSTRAINT uq_invoice_replacing_invoice UNIQUE (replacing_invoice_id),
    UNIQUE KEY uq_invoice_fulfillment_original (fulfillment_event_id, (CASE WHEN invoice_type = 'original' THEN 1 ELSE NULL END))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_invoice_order ON invoices(order_id);
CREATE INDEX idx_invoice_original ON invoices(original_invoice_id);
CREATE INDEX idx_invoice_doc_status ON invoices(document_status);
CREATE INDEX idx_invoice_tax_point ON invoices(tax_point_date);

CREATE TABLE invoice_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    invoice_id BIGINT NOT NULL,
    original_invoice_item_id BIGINT NULL,
    order_detail_id BIGINT NULL,
    fulfillment_event_item_id BIGINT NULL,
    item_type VARCHAR(30) NOT NULL,
    item_name VARCHAR(255) NOT NULL,
    unit VARCHAR(30) NOT NULL,
    original_quantity INT NOT NULL DEFAULT 0,
    quantity INT NOT NULL,
    unit_price_gross DECIMAL(15, 2) NOT NULL,
    unit_price_net DECIMAL(15, 4) NOT NULL,
    discount_amount DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    taxable_amount DECIMAL(15, 2) NOT NULL,
    tax_category VARCHAR(50) NOT NULL,
    tax_treatment VARCHAR(30) NOT NULL,
    tax_rate DECIMAL(5, 4) NULL,
    tax_code VARCHAR(20) NOT NULL,
    tax_amount DECIMAL(15, 2) NOT NULL,
    line_total_gross DECIMAL(15, 2) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_invoice_item_invoice FOREIGN KEY (invoice_id) REFERENCES invoices(id) ON DELETE CASCADE,
    CONSTRAINT fk_invoice_item_orig_item FOREIGN KEY (original_invoice_item_id) REFERENCES invoice_items(id) ON DELETE RESTRICT,
    CONSTRAINT fk_invoice_item_order_detail FOREIGN KEY (order_detail_id) REFERENCES order_details(id) ON DELETE RESTRICT,
    CONSTRAINT fk_invoice_item_fei FOREIGN KEY (fulfillment_event_item_id) REFERENCES fulfillment_event_items(id) ON DELETE RESTRICT,

    CONSTRAINT chk_invoice_item_type CHECK (item_type IN ('merchandise', 'shipping_fee')),
    CONSTRAINT chk_invoice_item_treatment CHECK (tax_treatment IN ('taxable', 'zero_rated', 'non_taxable', 'exempt'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_invoice_item_invoice ON invoice_items(invoice_id);
CREATE INDEX idx_invoice_item_orig ON invoice_items(original_invoice_item_id);
CREATE INDEX idx_invoice_item_order_detail ON invoice_items(order_detail_id);

CREATE TABLE invoice_balance_adjustments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    original_invoice_id BIGINT NOT NULL,
    invoice_item_id BIGINT NOT NULL,
    original_quantity INT NOT NULL,
    total_adjusted_quantity INT NOT NULL DEFAULT 0,
    remaining_quantity INT NOT NULL,
    original_taxable_amount DECIMAL(15, 2) NOT NULL,
    total_adjusted_taxable_amount DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    remaining_taxable_amount DECIMAL(15, 2) NOT NULL,
    original_tax_amount DECIMAL(15, 2) NOT NULL,
    total_adjusted_tax_amount DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    remaining_tax_amount DECIMAL(15, 2) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_iba_invoice FOREIGN KEY (original_invoice_id) REFERENCES invoices(id) ON DELETE RESTRICT,
    CONSTRAINT fk_iba_item FOREIGN KEY (invoice_item_id) REFERENCES invoice_items(id) ON DELETE RESTRICT,
    CONSTRAINT uq_iba_invoice_item UNIQUE (original_invoice_id, invoice_item_id),
    CONSTRAINT chk_iba_qty CHECK (remaining_quantity >= 0 AND total_adjusted_quantity >= 0),
    CONSTRAINT chk_iba_taxable CHECK (remaining_taxable_amount >= 0 AND total_adjusted_taxable_amount >= 0),
    CONSTRAINT chk_iba_tax CHECK (remaining_tax_amount >= 0 AND total_adjusted_tax_amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE invoice_attempts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    invoice_id BIGINT NOT NULL,
    provider_name VARCHAR(50) NOT NULL,
    endpoint VARCHAR(255) NOT NULL,
    request_type VARCHAR(50) NOT NULL,
    sanitized_request_payload TEXT NULL,
    response_payload TEXT NULL,
    http_status_code INT NULL,
    outcome VARCHAR(30) NOT NULL,
    error_message VARCHAR(500) NULL,
    attempted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_attempt_invoice FOREIGN KEY (invoice_id) REFERENCES invoices(id) ON DELETE CASCADE,
    CONSTRAINT chk_attempt_outcome CHECK (outcome IN ('success', 'failed', 'timeout_unknown'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_attempt_invoice ON invoice_attempts(invoice_id);
CREATE INDEX idx_attempt_attempted_at ON invoice_attempts(attempted_at);

CREATE TABLE invoice_outbox (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    event_key VARCHAR(150) NOT NULL,
    invoice_id BIGINT NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(30) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    available_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    locked_until DATETIME NULL,
    locked_by VARCHAR(100) NULL,
    lease_token VARCHAR(64) NULL,
    last_error TEXT NULL,
    processed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_outbox_invoice FOREIGN KEY (invoice_id) REFERENCES invoices(id) ON DELETE CASCADE,
    CONSTRAINT uq_outbox_event_key UNIQUE (event_key),
    CONSTRAINT chk_outbox_status CHECK (status IN ('pending', 'processing', 'processed', 'failed', 'dead_letter'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_outbox_claim ON invoice_outbox (status, available_at, locked_until);
CREATE INDEX idx_outbox_invoice ON invoice_outbox (invoice_id);
