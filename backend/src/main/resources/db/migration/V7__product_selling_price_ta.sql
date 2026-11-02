-- Giá bán lẻ / tạ lưu riêng với giá bán sỉ / tấn (default_selling_price = đ/kg kênh tấn).
-- Bán tạ (số lượng ít) thường đắt hơn bán tấn (số lượng lớn) — không quy đổi chéo nhau.

ALTER TABLE products
    ADD COLUMN default_selling_price_ta DECIMAL(19,4) NOT NULL DEFAULT 0
        AFTER default_selling_price;

-- Dữ liệu cũ: suy ra giá / tạ từ giá / kg đang có (1 tạ = 100 kg).
UPDATE products
SET default_selling_price_ta = ROUND(default_selling_price * 100, 4)
WHERE default_selling_price > 0;

ALTER TABLE products DROP CHECK chk_product_prices;

ALTER TABLE products
    ADD CONSTRAINT chk_product_prices CHECK (
        reference_purchase_price >= 0
        AND default_selling_price >= 0
        AND default_selling_price_ta >= 0
    );
