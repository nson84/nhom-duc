ALTER TABLE payroll_details
    ADD COLUMN advance_amount DECIMAL(19,4) NOT NULL DEFAULT 0,
    ADD COLUMN overtime_hours DECIMAL(12,3),
    ADD COLUMN holiday_bonus_received DECIMAL(19,4);

UPDATE payroll_details d
JOIN payroll_periods p ON p.id = d.payroll_period_id
SET d.overtime_hours = (
        SELECT COALESCE(SUM(o.overtime_hours), 0)
        FROM attendance_records a JOIN overtime_records o ON o.attendance_id = a.id
        WHERE a.employee_id = d.employee_id AND o.is_approved = 1
          AND YEAR(a.work_date) = p.period_year AND MONTH(a.work_date) = p.period_month
    ),
    d.holiday_bonus_received = (
        SELECT COALESCE(SUM(h.bonus_amount), 0)
        FROM holiday_employee_bonuses b JOIN holidays h ON h.id = b.holiday_id
        WHERE b.employee_id = d.employee_id AND b.received = 1
          AND YEAR(h.holiday_date) = p.period_year AND MONTH(h.holiday_date) = p.period_month
    )
WHERE p.status = 'CLOSED';
