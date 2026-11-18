CREATE TABLE shop_month_closures (
    shop_id BIGINT NOT NULL,
    month_start DATE NOT NULL,
    period_year SMALLINT NOT NULL,
    closed_at DATETIME(3) NOT NULL,
    PRIMARY KEY (shop_id, month_start),
    CONSTRAINT fk_shop_month_closures_shop FOREIGN KEY (shop_id) REFERENCES shops(id)
) ENGINE=InnoDB;
