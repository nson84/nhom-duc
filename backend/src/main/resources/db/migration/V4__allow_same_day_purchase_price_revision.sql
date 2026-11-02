-- Cho phep nhieu moc gia trong cung mot ngay.
--
-- Truong hop that: phieu nhap trong ngay da dung moc gia dang mo, roi Admin doi gia ngay hom do.
-- Khong the sua moc da dung (se lam lech chi phi chuan da chot), cung khong the dong moc do bang
-- ngay truoc chinh no. Vi vay cho phep mo moc moi cung ngay; moc co id lon hon la moc duoc ap dung.
-- Khoa duy nhat vi vay phai gom ca id, khong chi (product_id, effective_from).

ALTER TABLE product_purchase_prices
    DROP INDEX uq_purchase_price_from,
    ADD KEY idx_purchase_price_lookup_id (product_id, effective_from, id);
