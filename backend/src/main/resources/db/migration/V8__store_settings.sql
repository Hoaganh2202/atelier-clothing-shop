-- V8: Create store_settings table and seed default configuration

CREATE TABLE store_settings (
    id BIGINT NOT NULL PRIMARY KEY DEFAULT 1,
    store_name VARCHAR(150) NOT NULL,
    legal_name VARCHAR(255) NULL,
    tax_code VARCHAR(30) NULL,
    address VARCHAR(300) NULL,
    phone VARCHAR(30) NULL,
    email VARCHAR(150) NULL,
    logo_url VARCHAR(500) NULL,
    low_stock_threshold INT NOT NULL DEFAULT 10,
    default_shipping_fee DECIMAL(12,2) NOT NULL DEFAULT 30000.00,
    free_shipping_threshold DECIMAL(12,2) NOT NULL DEFAULT 500000.00,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by VARCHAR(150) NULL,
    CONSTRAINT chk_store_settings_single_row CHECK (id = 1),
    CONSTRAINT chk_store_settings_low_stock CHECK (low_stock_threshold >= 0),
    CONSTRAINT chk_store_settings_default_shipping_fee CHECK (default_shipping_fee >= 0),
    CONSTRAINT chk_store_settings_free_shipping_threshold CHECK (free_shipping_threshold >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO store_settings (
    id,
    store_name,
    legal_name,
    tax_code,
    address,
    phone,
    email,
    logo_url,
    low_stock_threshold,
    default_shipping_fee,
    free_shipping_threshold,
    updated_at,
    updated_by
) VALUES (
    1,
    'Closet Shop',
    'Công ty TNHH Thời trang Closet',
    '0101234567',
    '123 Đường Cầu Giấy, Phường Quan Hoa, Quận Cầu Giấy, Hà Nội',
    '0987654321',
    'contact@closetshop.com',
    '/uploads/products/logo.png',
    10,
    30000.00,
    500000.00,
    CURRENT_TIMESTAMP,
    'system'
);
