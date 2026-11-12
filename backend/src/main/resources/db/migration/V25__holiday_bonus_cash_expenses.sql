INSERT INTO financial_transactions (
    transaction_code, transaction_date, transaction_type, category, amount, description,
    reference_type, reference_id, status, idempotency_key, shop_id, is_internal
)
SELECT CONCAT('TL-', h.id, '-', e.id),
       COALESCE((SELECT MAX(a.created_at) FROM audit_logs a
                 WHERE a.entity_type = 'holidays' AND a.entity_id = CAST(h.id AS CHAR)
                   AND a.action IN ('CREATE_HOLIDAY', 'UPDATE_HOLIDAY')), h.holiday_date),
       'EXPENSE', 'PAYROLL', h.bonus_amount,
       CONCAT('Chi thưởng lễ: ', h.name, ' · ', e.full_name),
       'HOLIDAY_BONUS', h.id, 'CONFIRMED', CONCAT('holiday-bonus:', h.id, ':', e.id), e.shop_id, 0
FROM holiday_employee_bonuses b
JOIN holidays h ON h.id = b.holiday_id
JOIN employees e ON e.id = b.employee_id
WHERE b.received = 1 AND h.bonus_amount > 0;
