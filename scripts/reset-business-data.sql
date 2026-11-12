-- Clear business records for a fresh manual 2025 test. Keep shops, products,
-- product units, users, settings and bank accounts. Back up the database first.
SET FOREIGN_KEY_CHECKS = 0;
START TRANSACTION;

DELETE FROM factory_raw_count_layers;
DELETE FROM factory_raw_counts;
DELETE FROM factory_scrap_payments;
DELETE FROM factory_scrap_sales;
DELETE FROM sales_payments;
DELETE FROM customer_payment_reminders;
DELETE FROM stock_movements;
DELETE FROM stock_document_items;
DELETE FROM stock_documents;
DELETE FROM financial_transactions;
DELETE FROM distribution_period_closings;
DELETE FROM year_end_balances;
DELETE FROM factory_year_counts;
DELETE FROM payroll_details;
DELETE FROM overtime_records;
DELETE FROM attendance_records;
DELETE FROM employee_pay_rates;
DELETE FROM holiday_employee_bonuses;
DELETE FROM payroll_periods;
DELETE FROM employees;
DELETE FROM customers;
DELETE FROM attendance_day_locks;
DELETE FROM document_sequences;
DELETE FROM audit_logs;
DELETE FROM category_purchase_prices;
DELETE FROM product_purchase_prices;

UPDATE stock_balances SET quantity_base = 0, average_cost_per_base = 0;
INSERT INTO category_purchase_prices
    (category_id, effective_from, effective_to, price_per_base, note)
SELECT id, '2025-01-01', NULL, reference_purchase_price, 'Moc gia dau ky 2025'
FROM product_categories;

COMMIT;
SET FOREIGN_KEY_CHECKS = 1;
