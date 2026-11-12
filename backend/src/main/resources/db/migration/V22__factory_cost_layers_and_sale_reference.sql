-- Giá nhập tham chiếu trên phiếu bán xưởng và kiểm kê nguyên liệu theo loại nhôm.
ALTER TABLE stock_document_items
    ADD COLUMN factory_purchase_item_id BIGINT NULL,
    ADD COLUMN factory_reference_cost_per_kg DECIMAL(19,6) NULL,
    ADD KEY idx_factory_sale_reference (factory_reference_cost_per_kg, category_id),
    ADD CONSTRAINT fk_factory_sale_purchase_item FOREIGN KEY (factory_purchase_item_id)
        REFERENCES stock_document_items(id);

CREATE TABLE factory_raw_counts (
    year_number INT NOT NULL,
    category_id BIGINT NOT NULL,
    quantity_kg DECIMAL(19,6) NOT NULL,
    value_amount DECIMAL(19,4) NOT NULL,
    PRIMARY KEY (year_number, category_id),
    CONSTRAINT fk_factory_raw_category FOREIGN KEY (category_id) REFERENCES product_categories(id),
    CONSTRAINT chk_factory_raw_quantity CHECK (quantity_kg >= 0 AND value_amount >= 0)
) ENGINE=InnoDB;

CREATE TABLE factory_raw_count_layers (
    year_number INT NOT NULL,
    category_id BIGINT NOT NULL,
    purchase_item_id BIGINT NOT NULL,
    quantity_kg DECIMAL(19,6) NOT NULL,
    unit_cost_per_kg DECIMAL(19,6) NOT NULL,
    value_amount DECIMAL(19,4) NOT NULL,
    PRIMARY KEY (year_number, category_id, purchase_item_id),
    CONSTRAINT fk_factory_layer_count FOREIGN KEY (year_number, category_id)
        REFERENCES factory_raw_counts(year_number, category_id) ON DELETE CASCADE,
    CONSTRAINT fk_factory_layer_item FOREIGN KEY (purchase_item_id) REFERENCES stock_document_items(id)
) ENGINE=InnoDB;
