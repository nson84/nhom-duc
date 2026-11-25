ALTER TABLE stock_documents ADD COLUMN paper_source BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE stock_document_items ADD COLUMN entered_cost_per_kg DECIMAL(18,6) NULL;
ALTER TABLE financial_transactions ADD COLUMN monthly_summary BOOLEAN NOT NULL DEFAULT FALSE;
