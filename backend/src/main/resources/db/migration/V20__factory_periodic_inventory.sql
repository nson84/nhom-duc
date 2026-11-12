-- Xưởng quyết toán nhôm theo kiểm kê năm; phế liệu bán riêng, không nấu lại.
CREATE TABLE factory_scrap_sales (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    shop_id BIGINT NOT NULL,
    sale_date DATETIME(3) NOT NULL,
    buyer_name VARCHAR(200) NOT NULL,
    seller_name VARCHAR(150) NOT NULL,
    quantity_kg DECIMAL(19,6) NOT NULL,
    unit_price DECIMAL(19,4) NOT NULL,
    total_amount DECIMAL(19,4) NOT NULL,
    notes VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED',
    void_reason VARCHAR(500),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_factory_scrap_shop FOREIGN KEY (shop_id) REFERENCES shops(id),
    CONSTRAINT chk_factory_scrap_qty CHECK (quantity_kg > 0),
    CONSTRAINT chk_factory_scrap_price CHECK (unit_price >= 0 AND total_amount >= 0),
    CONSTRAINT chk_factory_scrap_status CHECK (status IN ('CONFIRMED', 'VOID')),
    KEY idx_factory_scrap_date (shop_id, status, sale_date)
) ENGINE=InnoDB;

CREATE TABLE factory_scrap_payments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    scrap_sale_id BIGINT NOT NULL,
    payment_date DATETIME(3) NOT NULL,
    amount DECIMAL(19,4) NOT NULL,
    payment_method VARCHAR(30) NOT NULL,
    collected_by VARCHAR(80) NOT NULL,
    financial_transaction_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_factory_scrap_payment_sale FOREIGN KEY (scrap_sale_id) REFERENCES factory_scrap_sales(id),
    CONSTRAINT fk_factory_scrap_payment_finance FOREIGN KEY (financial_transaction_id) REFERENCES financial_transactions(id),
    CONSTRAINT chk_factory_scrap_payment_amount CHECK (amount > 0),
    UNIQUE KEY uq_factory_scrap_payment_finance (financial_transaction_id),
    KEY idx_factory_scrap_payment_date (payment_date)
) ENGINE=InnoDB;

CREATE TABLE factory_year_counts (
    year_number INT PRIMARY KEY,
    opening_kg DECIMAL(19,6) NOT NULL DEFAULT 0,
    opening_value DECIMAL(19,4) NOT NULL DEFAULT 0,
    raw_kg DECIMAL(19,6) NOT NULL DEFAULT 0,
    raw_value DECIMAL(19,4) NOT NULL DEFAULT 0,
    scrap_kg DECIMAL(19,6) NOT NULL DEFAULT 0,
    scrap_value DECIMAL(19,4) NOT NULL DEFAULT 0,
    work_in_progress_kg DECIMAL(19,6) NOT NULL DEFAULT 0,
    work_in_progress_value DECIMAL(19,4) NOT NULL DEFAULT 0,
    finished_kg DECIMAL(19,6) NOT NULL DEFAULT 0,
    finished_value DECIMAL(19,4) NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    notes VARCHAR(500),
    closed_at DATETIME(3),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT chk_factory_count_status CHECK (status IN ('DRAFT', 'CLOSED')),
    CONSTRAINT chk_factory_count_kg CHECK (opening_kg >= 0 AND raw_kg >= 0 AND scrap_kg >= 0
        AND work_in_progress_kg >= 0 AND finished_kg >= 0),
    CONSTRAINT chk_factory_count_value CHECK (opening_value >= 0 AND raw_value >= 0 AND scrap_value >= 0
        AND work_in_progress_value >= 0 AND finished_value >= 0)
) ENGINE=InnoDB;
