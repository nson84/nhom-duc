ALTER TABLE year_period_starts
    ADD COLUMN period_name VARCHAR(100) NULL;

UPDATE year_period_starts
SET period_name = CONCAT('Năm ', year_number);

ALTER TABLE year_period_starts
    MODIFY period_name VARCHAR(100) NOT NULL;
