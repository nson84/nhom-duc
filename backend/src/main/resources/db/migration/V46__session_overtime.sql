ALTER TABLE employee_pay_rates
    ADD COLUMN overtime_session_rate DECIMAL(19,4) NOT NULL DEFAULT 0,
    ADD CONSTRAINT chk_overtime_session_rate CHECK (overtime_session_rate >= 0);

ALTER TABLE overtime_records
    ADD COLUMN pay_unit VARCHAR(10) NOT NULL DEFAULT 'HOUR',
    ADD CONSTRAINT chk_overtime_pay_unit CHECK (pay_unit IN ('HOUR', 'SESSION'));
