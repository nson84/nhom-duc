-- Hai cửa hàng trong một sổ: phân phối nhôm khối và xưởng sản xuất.
-- Dữ liệu cũ thuộc xưởng. Xưởng được chèn trước để id = 1, làm mặc định khi phục hồi bản sao lưu cũ.

CREATE TABLE shops (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(30) NOT NULL,
    name VARCHAR(150) NOT NULL,
    has_payroll BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE KEY uq_shop_code (code)
) ENGINE=InnoDB;

INSERT INTO shops (code, name, has_payroll) VALUES
    ('MANUFACTURING', 'Xưởng sản xuất', 1),
    ('DISTRIBUTION', 'Phân phối nhôm khối', 0);

ALTER TABLE product_categories DROP INDEX uq_category_name;
ALTER TABLE product_categories
    ADD COLUMN shop_id BIGINT NOT NULL DEFAULT 1,
    ADD CONSTRAINT fk_categories_shop FOREIGN KEY (shop_id) REFERENCES shops (id),
    ADD UNIQUE KEY uq_category_shop_name (shop_id, name);

ALTER TABLE products DROP INDEX uq_product_code;
ALTER TABLE products
    ADD COLUMN shop_id BIGINT NOT NULL DEFAULT 1,
    ADD CONSTRAINT fk_products_shop FOREIGN KEY (shop_id) REFERENCES shops (id),
    ADD UNIQUE KEY uq_product_shop_code (shop_id, product_code),
    ADD KEY idx_products_shop (shop_id);

ALTER TABLE stock_documents
    ADD COLUMN shop_id BIGINT NOT NULL DEFAULT 1,
    ADD COLUMN is_internal BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT fk_documents_shop FOREIGN KEY (shop_id) REFERENCES shops (id),
    ADD KEY idx_documents_shop_date (shop_id, document_date);

ALTER TABLE financial_transactions
    ADD COLUMN shop_id BIGINT NOT NULL DEFAULT 1,
    ADD COLUMN is_internal BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT fk_finance_shop FOREIGN KEY (shop_id) REFERENCES shops (id),
    ADD KEY idx_finance_shop_date (shop_id, transaction_date);

ALTER TABLE employees
    ADD COLUMN shop_id BIGINT NOT NULL DEFAULT 1,
    ADD CONSTRAINT fk_employees_shop FOREIGN KEY (shop_id) REFERENCES shops (id);
