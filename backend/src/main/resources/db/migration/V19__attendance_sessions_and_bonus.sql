ALTER TABLE attendance_records
    ADD COLUMN morning_present BOOLEAN NOT NULL DEFAULT FALSE AFTER attendance_status,
    ADD COLUMN afternoon_present BOOLEAN NOT NULL DEFAULT FALSE AFTER morning_present,
    ADD COLUMN bonus_amount DECIMAL(19,4) NOT NULL DEFAULT 0 AFTER daily_rate_snapshot,
    ADD COLUMN bonus_notes VARCHAR(500) NULL AFTER bonus_amount,
    ADD CONSTRAINT chk_attendance_bonus CHECK (bonus_amount >= 0);

UPDATE attendance_records
SET morning_present = attendance_status IN ('PRESENT', 'HALF_DAY'),
    afternoon_present = attendance_status = 'PRESENT';

ALTER TABLE payroll_details
    ADD COLUMN bonus_pay DECIMAL(19,4) NOT NULL DEFAULT 0 AFTER overtime_pay;

ALTER TABLE payroll_details DROP CHECK chk_payroll_total;
ALTER TABLE payroll_details
    ADD CONSTRAINT chk_payroll_bonus CHECK (bonus_pay >= 0),
    ADD CONSTRAINT chk_payroll_total CHECK (
        total_pay = base_pay + overtime_pay + bonus_pay + allowances - deductions
    );
