-- Lưu bằng chứng kiểm kê ngay cả khi số cân bằng tồn sổ (không phát sinh phiếu điều chỉnh).
-- Chỉ dùng để xác minh đã cân; kết quả tồn sau kiểm kê vẫn thuộc stock_balances / stock_movements.
CREATE TABLE distribution_inventory_count_evidence (
    shop_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    counted_quantity_kg DECIMAL(19,6) NOT NULL,
    counted_at DATETIME(3) NOT NULL,
    PRIMARY KEY (shop_id, product_id, category_id),
    CONSTRAINT fk_dist_count_shop FOREIGN KEY (shop_id) REFERENCES shops(id),
    CONSTRAINT fk_dist_count_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_dist_count_category FOREIGN KEY (category_id) REFERENCES product_categories(id),
    CONSTRAINT chk_dist_count_nonnegative CHECK (counted_quantity_kg >= 0)
) ENGINE=InnoDB;
