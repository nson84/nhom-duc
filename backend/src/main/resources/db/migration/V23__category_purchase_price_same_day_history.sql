-- Keep each price change in history even when the new price starts on the same date.
-- The newest id wins for date-based price lookup; older rows remain for audit.
ALTER TABLE category_purchase_prices
    DROP INDEX uq_cat_purchase_price_from,
    ADD KEY idx_cat_purchase_price_lookup_id (category_id, effective_from, id);
