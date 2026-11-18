CREATE TABLE year_period_starts (
    shop_id BIGINT NOT NULL,
    year_number INT NOT NULL,
    period_from DATE NOT NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (shop_id, year_number),
    CONSTRAINT fk_year_period_starts_shop FOREIGN KEY (shop_id) REFERENCES shops(id)
);

INSERT INTO year_period_starts (shop_id, year_number, period_from)
SELECT shop_id, year_number, period_from FROM year_period_drafts;

INSERT INTO year_period_starts (shop_id, year_number, period_from)
SELECT s.id, c.year_number, c.period_from
FROM factory_year_counts c JOIN shops s ON s.code = 'MANUFACTURING'
WHERE NOT EXISTS (
    SELECT 1 FROM year_period_starts p WHERE p.shop_id = s.id AND p.year_number = c.year_number
);

INSERT INTO year_period_drafts (shop_id, year_number, period_from, period_to)
SELECT s.id, c.year_number, c.period_from, c.period_to
FROM factory_year_counts c JOIN shops s ON s.code = 'MANUFACTURING'
WHERE NOT EXISTS (
    SELECT 1 FROM year_period_drafts p WHERE p.shop_id = s.id AND p.year_number = c.year_number
);
