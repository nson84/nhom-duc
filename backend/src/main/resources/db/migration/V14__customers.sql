-- Sổ khách mua theo shop + gắn phiếu bán + lịch nhắc nợ.

CREATE TABLE customers (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    shop_id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL,
    phone VARCHAR(30) NULL,
    note VARCHAR(500) NULL,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_customers_shop FOREIGN KEY (shop_id) REFERENCES shops (id),
    UNIQUE KEY uq_customers_shop_name (shop_id, name),
    KEY idx_customers_shop_phone (shop_id, phone)
) ENGINE=InnoDB;

ALTER TABLE stock_documents
    ADD COLUMN customer_id BIGINT NULL AFTER counterparty_name,
    ADD CONSTRAINT fk_documents_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    ADD KEY idx_documents_customer (customer_id);

CREATE TABLE customer_payment_reminders (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,
    document_id BIGINT NULL,
    remind_on DATE NOT NULL,
    note VARCHAR(500) NULL,
    is_done TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_reminders_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT fk_reminders_document FOREIGN KEY (document_id) REFERENCES stock_documents (id),
    KEY idx_reminders_due (remind_on, is_done),
    KEY idx_reminders_customer (customer_id)
) ENGINE=InnoDB;
