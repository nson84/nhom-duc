-- Match the existing AttendanceService net-pay calculation; never rewrite historical amounts.
-- One atomic ALTER retains the original constraint if existing rows fail validation.
ALTER TABLE payroll_details
    DROP CHECK chk_payroll_total,
    ADD CONSTRAINT chk_payroll_total CHECK (
        total_pay = GREATEST(base_pay + overtime_pay + bonus_pay + allowances - deductions - advance_amount, 0)
    );
