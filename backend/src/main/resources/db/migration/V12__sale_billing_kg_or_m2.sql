-- Bán hàng chọn kg hoặc m² trên phiếu. Tồn cơ sở luôn theo kg.
-- Giá gợi ý / m² + hệ số kg/m² trên danh mục; snapshot rộng/cao trên dòng phiếu.

-- SP từng tạo kiểu AREA (tồn m²) → về kg; giá cũ coi như giá / m² gợi ý.
ALTER TABLE products
    ADD COLUMN default_selling_price_m2 DECIMAL(19,4) NOT NULL DEFAULT 0
        AFTER default_selling_price_ta,
    ADD COLUMN kg_per_m2 DECIMAL(19,8) NULL
        AFTER default_selling_price_m2;

UPDATE products
SET default_selling_price_m2 = default_selling_price,
    default_selling_price = 0,
    default_selling_price_ta = 0,
    base_unit_name = 'kg',
    sell_mode = 'AREA'
WHERE sell_mode = 'AREA' AND base_unit_name IN ('m²', 'm2');

UPDATE product_units u
JOIN products p ON p.id = u.product_id
SET u.unit_name = 'kg', u.factor_to_base = 1
WHERE u.is_base_unit = 1 AND p.sell_mode = 'AREA' AND u.unit_name IN ('m²', 'm2');

ALTER TABLE products
    ADD CONSTRAINT chk_product_kg_per_m2 CHECK (kg_per_m2 IS NULL OR kg_per_m2 > 0),
    ADD CONSTRAINT chk_product_price_m2 CHECK (default_selling_price_m2 >= 0);

ALTER TABLE stock_document_items
    ADD COLUMN billing_unit VARCHAR(10) NULL AFTER unit_name_snapshot,
    ADD COLUMN width_m DECIMAL(19,6) NULL AFTER billing_unit,
    ADD COLUMN height_m DECIMAL(19,6) NULL AFTER width_m;

ALTER TABLE stock_document_items
    ADD CONSTRAINT chk_item_billing_unit CHECK (
        billing_unit IS NULL OR billing_unit IN ('kg', 'm2')
    ),
    ADD CONSTRAINT chk_item_dimensions CHECK (
        (width_m IS NULL AND height_m IS NULL)
        OR (width_m IS NOT NULL AND height_m IS NOT NULL AND width_m > 0 AND height_m > 0)
    );
