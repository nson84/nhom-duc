-- Tránh ghi trùng phiếu phế liệu hoặc lần thu khi người dùng gửi lại sau mất kết nối.
ALTER TABLE factory_scrap_sales
    ADD COLUMN idempotency_key VARCHAR(80) NULL,
    ADD UNIQUE KEY uq_factory_scrap_sale_key (idempotency_key);

ALTER TABLE factory_scrap_payments
    ADD COLUMN idempotency_key VARCHAR(80) NULL,
    ADD UNIQUE KEY uq_factory_scrap_payment_key (idempotency_key);
