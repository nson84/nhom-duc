-- Gia nhap thay doi theo ngay: luu lich su gia nhap chuan theo khoang hieu luc.
-- Moi dong phieu luu snapshot gia chuan tai ngay phieu de tinh chi phi chuan on dinh,
-- khong bi lech khi Admin sua gia nhap ve sau.
--
-- effective_from: ngay bat dau ap dung (tinh ca ngay).
-- effective_to: ngay cuoi con ap dung (tinh ca ngay); NULL = dang ap dung.
-- Mot san pham chi co mot dong dang mo (effective_to IS NULL) tai mot thoi diem.

CREATE TABLE product_purchase_prices (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE,
    price_per_base DECIMAL(19,6) NOT NULL,
    note VARCHAR(500),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uq_purchase_price_from (product_id, effective_from),
    KEY idx_purchase_price_lookup (product_id, effective_from, effective_to),
    KEY idx_purchase_price_open (product_id, effective_to),
    CONSTRAINT fk_purchase_price_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT chk_purchase_price_amount CHECK (price_per_base >= 0),
    CONSTRAINT chk_purchase_price_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)
) ENGINE=InnoDB;

-- Gia chuan / chi phi chuan snapshot tren tung dong phieu.
-- purchase_price_id tro toi dong lich su gia da dung, de truy vet.
ALTER TABLE stock_document_items
    ADD COLUMN purchase_price_id BIGINT NULL AFTER cost_total_snapshot,
    ADD COLUMN standard_cost_per_base_snapshot DECIMAL(19,6) NULL AFTER purchase_price_id,
    ADD COLUMN standard_cost_total_snapshot DECIMAL(19,4) NULL AFTER standard_cost_per_base_snapshot,
    ADD CONSTRAINT fk_item_purchase_price FOREIGN KEY (purchase_price_id) REFERENCES product_purchase_prices (id);

CREATE INDEX idx_items_purchase_price ON stock_document_items (purchase_price_id);

-- Khoi tao lich su tu gia nhap hien tai cua tung san pham.
-- Khong suy doan gia qua khu: dung mot moc duy nhat tu ngay tao san pham.
INSERT INTO product_purchase_prices (product_id, effective_from, price_per_base, note)
SELECT id, DATE(created_at), reference_purchase_price, 'Khởi tạo khi bật lịch sử giá nhập'
FROM products;
