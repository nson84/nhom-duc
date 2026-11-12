ALTER TABLE year_end_balances
    ADD COLUMN period_from DATE NULL,
    ADD COLUMN period_to DATE NULL;

UPDATE year_end_balances
SET period_from = MAKEDATE(year_number, 1),
    period_to = DATE_SUB(MAKEDATE(year_number + 1, 1), INTERVAL 1 DAY);

ALTER TABLE year_end_balances
    MODIFY period_from DATE NOT NULL,
    MODIFY period_to DATE NOT NULL;

ALTER TABLE factory_year_counts
    ADD COLUMN period_from DATE NULL,
    ADD COLUMN period_to DATE NULL;

UPDATE factory_year_counts
SET period_from = MAKEDATE(year_number, 1),
    period_to = DATE_SUB(MAKEDATE(year_number + 1, 1), INTERVAL 1 DAY);

ALTER TABLE factory_year_counts
    MODIFY period_from DATE NOT NULL,
    MODIFY period_to DATE NOT NULL;
