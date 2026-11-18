RENAME TABLE shop_month_closures TO shop_month_periods;

ALTER TABLE shop_month_periods
    MODIFY COLUMN closed_at DATETIME(3) NULL,
    ADD COLUMN opened_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3);
