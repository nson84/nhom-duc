-- Giá trên loại nhôm; tồn & dòng phiếu theo (product_id, category_id).
-- SP chỉ còn danh mục (mã/tên/ảnh); mọi SP dùng được mọi loại trong shop.

-- 1) Giá bán/nhập trên loại nhôm
ALTER TABLE product_categories
    ADD COLUMN reference_purchase_price DECIMAL(19,4) NOT NULL DEFAULT 0
        AFTER description,
    ADD COLUMN default_selling_price DECIMAL(19,4) NOT NULL DEFAULT 0
        AFTER reference_purchase_price,
    ADD COLUMN default_selling_price_ta DECIMAL(19,4) NOT NULL DEFAULT 0
        AFTER default_selling_price,
    ADD COLUMN default_selling_price_ton DECIMAL(19,4) NOT NULL DEFAULT 0
        AFTER default_selling_price_ta,
    ADD COLUMN default_selling_price_m2 DECIMAL(19,4) NOT NULL DEFAULT 0
        AFTER default_selling_price_ton;

ALTER TABLE product_categories
    ADD CONSTRAINT chk_category_prices CHECK (
        reference_purchase_price >= 0
        AND default_selling_price >= 0
        AND default_selling_price_ta >= 0
        AND default_selling_price_ton >= 0
        AND default_selling_price_m2 >= 0
    );

-- Copy giá từ SP mới nhất (active ưu tiên) đang gắn loại
UPDATE product_categories c
JOIN (
    SELECT p.category_id,
           p.reference_purchase_price,
           p.default_selling_price,
           p.default_selling_price_ta,
           COALESCE(p.default_selling_price_m2, 0) AS default_selling_price_m2,
           ROW_NUMBER() OVER (
               PARTITION BY p.category_id
               ORDER BY p.is_active DESC, p.updated_at DESC, p.id DESC
           ) AS rn
    FROM products p
    WHERE p.category_id IS NOT NULL
) src ON src.category_id = c.id AND src.rn = 1
SET c.reference_purchase_price = src.reference_purchase_price,
    c.default_selling_price = src.default_selling_price,
    c.default_selling_price_ta = COALESCE(src.default_selling_price_ta, src.default_selling_price * 100),
    c.default_selling_price_ton = src.default_selling_price * 1000,
    c.default_selling_price_m2 = src.default_selling_price_m2;

-- 2) Lịch sử giá nhập theo loại
CREATE TABLE category_purchase_prices (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    category_id BIGINT NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE,
    price_per_base DECIMAL(19,6) NOT NULL,
    note VARCHAR(500),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uq_cat_purchase_price_from (category_id, effective_from),
    KEY idx_cat_purchase_price_lookup (category_id, effective_from, effective_to),
    KEY idx_cat_purchase_price_open (category_id, effective_to),
    CONSTRAINT fk_cat_purchase_price_category FOREIGN KEY (category_id) REFERENCES product_categories (id),
    CONSTRAINT chk_cat_purchase_price_amount CHECK (price_per_base >= 0),
    CONSTRAINT chk_cat_purchase_price_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)
) ENGINE=InnoDB;

INSERT INTO category_purchase_prices (category_id, effective_from, effective_to, price_per_base, note, created_at, updated_at)
SELECT p.category_id,
       pp.effective_from,
       pp.effective_to,
       pp.price_per_base,
       pp.note,
       pp.created_at,
       pp.updated_at
FROM product_purchase_prices pp
JOIN products p ON p.id = pp.product_id
WHERE p.category_id IS NOT NULL
ORDER BY pp.id;

-- Mốc mở nếu loại chưa có lịch sử
INSERT INTO category_purchase_prices (category_id, effective_from, price_per_base, note)
SELECT c.id, CURDATE(), c.reference_purchase_price, 'Khởi tạo từ giá loại (V13)'
FROM product_categories c
WHERE NOT EXISTS (
    SELECT 1 FROM category_purchase_prices cpp WHERE cpp.category_id = c.id
);

-- 3) Dòng phiếu + biến động: gắn loại
ALTER TABLE stock_document_items
    ADD COLUMN category_id BIGINT NULL AFTER product_id,
    ADD COLUMN category_name_snapshot VARCHAR(100) NULL AFTER product_name_snapshot;

UPDATE stock_document_items i
JOIN products p ON p.id = i.product_id
JOIN product_categories c ON c.id = p.category_id
SET i.category_id = c.id,
    i.category_name_snapshot = c.name
WHERE i.category_id IS NULL;

-- Dòng thiếu loại (SP không còn category) — gắn loại đầu tiên cùng shop nếu có
UPDATE stock_document_items i
JOIN products p ON p.id = i.product_id
JOIN product_categories c ON c.shop_id = p.shop_id
SET i.category_id = c.id,
    i.category_name_snapshot = COALESCE(i.category_name_snapshot, c.name)
WHERE i.category_id IS NULL
  AND c.id = (
      SELECT MIN(c2.id) FROM product_categories c2 WHERE c2.shop_id = p.shop_id
  );

ALTER TABLE stock_document_items
    MODIFY COLUMN category_id BIGINT NOT NULL,
    MODIFY COLUMN category_name_snapshot VARCHAR(100) NOT NULL,
    ADD CONSTRAINT fk_items_category FOREIGN KEY (category_id) REFERENCES product_categories (id);

ALTER TABLE stock_movements
    ADD COLUMN category_id BIGINT NULL AFTER product_id;

UPDATE stock_movements m
JOIN stock_document_items i ON i.id = m.document_item_id
SET m.category_id = i.category_id
WHERE m.category_id IS NULL;

ALTER TABLE stock_movements
    MODIFY COLUMN category_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_movement_category FOREIGN KEY (category_id) REFERENCES product_categories (id),
    ADD KEY idx_movement_product_category_date (product_id, category_id, movement_at);

-- 4) Snapshot giá chuẩn trỏ sang category_purchase_prices
ALTER TABLE stock_document_items
    DROP FOREIGN KEY fk_item_purchase_price;

-- Map id cũ → id mới theo (category, effective_from, price)
UPDATE stock_document_items i
JOIN product_purchase_prices oldp ON oldp.id = i.purchase_price_id
JOIN products p ON p.id = oldp.product_id
JOIN category_purchase_prices newt ON newt.category_id = p.category_id
    AND newt.effective_from = oldp.effective_from
    AND newt.price_per_base = oldp.price_per_base
SET i.purchase_price_id = newt.id
WHERE i.purchase_price_id IS NOT NULL;

UPDATE stock_document_items SET purchase_price_id = NULL
WHERE purchase_price_id IS NOT NULL
  AND purchase_price_id NOT IN (SELECT id FROM category_purchase_prices);

ALTER TABLE stock_document_items
    ADD CONSTRAINT fk_item_category_purchase_price
        FOREIGN KEY (purchase_price_id) REFERENCES category_purchase_prices (id);

-- 5) Tồn theo (product, category)
DROP TRIGGER IF EXISTS trg_products_stock_balance;

CREATE TABLE stock_balances_v13 (
    product_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    quantity_base DECIMAL(19,6) NOT NULL DEFAULT 0,
    average_cost_per_base DECIMAL(19,6) NOT NULL DEFAULT 0,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (product_id, category_id),
    CONSTRAINT fk_stock_balances_v13_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_stock_balances_v13_category FOREIGN KEY (category_id) REFERENCES product_categories (id),
    CONSTRAINT chk_stock_v13_quantity CHECK (quantity_base >= 0),
    CONSTRAINT chk_stock_v13_cost CHECK (average_cost_per_base >= 0)
) ENGINE=InnoDB;

INSERT INTO stock_balances_v13 (product_id, category_id, quantity_base, average_cost_per_base, updated_at)
SELECT b.product_id,
       p.category_id,
       b.quantity_base,
       b.average_cost_per_base,
       b.updated_at
FROM stock_balances b
JOIN products p ON p.id = b.product_id
WHERE p.category_id IS NOT NULL;

DROP TABLE stock_balances;
RENAME TABLE stock_balances_v13 TO stock_balances;

-- 6) SP: category_id nullable; giá SP không còn dùng trên form (giữ cột để tương thích đọc cũ)
ALTER TABLE products
    DROP FOREIGN KEY fk_products_category;

ALTER TABLE products
    MODIFY COLUMN category_id BIGINT NULL;

ALTER TABLE products
    ADD CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES product_categories (id);
