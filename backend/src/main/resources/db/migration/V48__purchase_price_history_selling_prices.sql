ALTER TABLE category_purchase_prices
    ADD COLUMN selling_price_kg DECIMAL(19,4) NULL,
    ADD COLUMN selling_price_ta DECIMAL(19,4) NULL,
    ADD COLUMN selling_price_ton DECIMAL(19,4) NULL;
