-- Read-only report. Run against an independent V42 copy before attempting V43.
-- Never rewrite historical pay or run Flyway repair automatically to bypass a failed upgrade.
SELECT d.id AS payroll_detail_id, d.employee_id, d.payroll_period_id,
       p.period_year, p.period_month, p.status AS period_status,
       d.base_pay, d.overtime_pay, d.bonus_pay, d.allowances, d.deductions,
       d.advance_amount, d.total_pay AS stored_total,
       GREATEST(d.base_pay + d.overtime_pay + d.bonus_pay + d.allowances
                - d.deductions - d.advance_amount, 0) AS expected_by_existing_formula,
       d.total_pay - GREATEST(d.base_pay + d.overtime_pay + d.bonus_pay + d.allowances
                - d.deductions - d.advance_amount, 0) AS difference,
       CASE WHEN d.advance_amount > 0 THEN 'Historical total does not match the formula including advances'
            ELSE 'Historical total does not match the current net-pay formula' END AS reason,
       (SELECT COALESCE(SUM(f.amount), 0) FROM financial_transactions f
        WHERE f.reference_type = 'PAYROLL_PERIOD' AND f.reference_id = p.id
          AND f.category = 'PAYROLL' AND f.status = 'CONFIRMED') AS paid_for_entire_period
FROM payroll_details d JOIN payroll_periods p ON p.id = d.payroll_period_id
WHERE d.total_pay <> GREATEST(d.base_pay + d.overtime_pay + d.bonus_pay + d.allowances
                              - d.deductions - d.advance_amount, 0)
ORDER BY p.period_year, p.period_month, d.id;
