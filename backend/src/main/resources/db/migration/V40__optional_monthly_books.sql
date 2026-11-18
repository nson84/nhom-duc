CREATE TABLE monthly_books (
    shop_id BIGINT NOT NULL,
    month_start DATE NOT NULL,
    period_year SMALLINT NOT NULL,
    snapshot_json LONGTEXT NOT NULL,
    closed_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (shop_id, month_start),
    CONSTRAINT fk_monthly_books_shop FOREIGN KEY (shop_id) REFERENCES shops(id)
) ENGINE=InnoDB;
