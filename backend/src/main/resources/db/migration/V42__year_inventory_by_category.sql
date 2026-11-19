CREATE TABLE year_end_inventory_categories (
    shop_id BIGINT NOT NULL,
    year_number INT NOT NULL,
    category_id BIGINT NOT NULL,
    category_name VARCHAR(200) NOT NULL,
    raw_kg DECIMAL(19,6) NOT NULL,
    PRIMARY KEY (shop_id, year_number, category_id),
    CONSTRAINT fk_year_inventory_balance FOREIGN KEY (shop_id, year_number)
        REFERENCES year_end_balances (shop_id, year_number),
    CONSTRAINT fk_year_inventory_category FOREIGN KEY (category_id) REFERENCES product_categories(id),
    CONSTRAINT chk_year_inventory_kg CHECK (raw_kg >= 0)
) ENGINE=InnoDB;
