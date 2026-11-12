CREATE TABLE year_end_balances (
    shop_id BIGINT NOT NULL,
    year_number INT NOT NULL,
    inventory_kg DECIMAL(19,6) NOT NULL,
    inventory_value DECIMAL(19,4) NOT NULL,
    customer_debt DECIMAL(19,4) NOT NULL,
    supplier_debt DECIMAL(19,4) NOT NULL,
    profit DECIMAL(19,4) NOT NULL,
    closed_at DATETIME(3) NOT NULL,
    PRIMARY KEY (shop_id, year_number),
    FOREIGN KEY (shop_id) REFERENCES shops(id)
);
