-- Kiểu bán: WEIGHT (kg/tấn) hoặc AREA (m²). Mỗi SP một đơn vị tồn cơ sở.
ALTER TABLE products
    ADD COLUMN sell_mode VARCHAR(20) NOT NULL DEFAULT 'WEIGHT' AFTER base_unit_name;

ALTER TABLE products
    ADD CONSTRAINT chk_product_sell_mode CHECK (sell_mode IN ('WEIGHT', 'AREA'));
