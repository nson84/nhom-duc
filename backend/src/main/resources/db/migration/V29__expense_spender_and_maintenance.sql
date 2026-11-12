ALTER TABLE financial_transactions ADD COLUMN spent_by VARCHAR(150);
ALTER TABLE financial_transactions DROP CHECK chk_finance_category_type;
ALTER TABLE financial_transactions ADD CONSTRAINT chk_finance_category_type CHECK (
  (transaction_type = 'INCOME' AND category IN ('SALE_RECEIPT', 'OTHER_INCOME'))
  OR (transaction_type = 'EXPENSE' AND category IN ('PURCHASE_PAYMENT', 'OPERATING_EXPENSE', 'MAINTENANCE', 'PAYROLL', 'OTHER_EXPENSE'))
);
