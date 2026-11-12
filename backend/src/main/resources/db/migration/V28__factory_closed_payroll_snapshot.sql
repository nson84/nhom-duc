ALTER TABLE factory_year_counts ADD COLUMN closed_payroll_cost DECIMAL(19,4);

UPDATE factory_year_counts c SET closed_payroll_cost =
    (SELECT COALESCE(SUM(GREATEST(pd.base_pay + pd.overtime_pay + pd.bonus_pay + pd.allowances - pd.deductions, 0)
        - pd.holiday_bonus_pay), 0)
     FROM payroll_details pd JOIN payroll_periods pp ON pp.id = pd.payroll_period_id
     JOIN employees e ON e.id = pd.employee_id JOIN shops s ON s.id = e.shop_id
     WHERE pp.period_year = c.year_number AND s.code = 'MANUFACTURING')
    + (SELECT COALESCE(SUM(h.bonus_amount), 0)
       FROM holiday_employee_bonuses b JOIN holidays h ON h.id = b.holiday_id
       JOIN employees e ON e.id = b.employee_id JOIN shops s ON s.id = e.shop_id
       WHERE YEAR(h.holiday_date) = c.year_number AND s.code = 'MANUFACTURING')
WHERE c.status = 'CLOSED';
