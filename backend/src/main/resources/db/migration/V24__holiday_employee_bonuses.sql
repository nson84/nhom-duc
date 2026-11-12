ALTER TABLE holidays ADD COLUMN bonus_amount DECIMAL(19,4) NOT NULL DEFAULT 0;

CREATE TABLE holiday_employee_bonuses (
    holiday_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    received BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (holiday_id, employee_id),
    FOREIGN KEY (holiday_id) REFERENCES holidays(id),
    FOREIGN KEY (employee_id) REFERENCES employees(id)
);

ALTER TABLE payroll_details ADD COLUMN holiday_bonus_pay DECIMAL(19,4) NOT NULL DEFAULT 0;
