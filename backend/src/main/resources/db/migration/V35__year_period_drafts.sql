CREATE TABLE year_period_drafts (
    shop_id BIGINT NOT NULL,
    year_number INT NOT NULL,
    period_from DATE NOT NULL,
    period_to DATE NOT NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (shop_id, year_number),
    CONSTRAINT fk_year_period_drafts_shop FOREIGN KEY (shop_id) REFERENCES shops(id)
);
