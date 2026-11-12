CREATE TABLE distribution_period_closings (
  shop_id BIGINT NOT NULL,
  period_from DATE NOT NULL,
  period_to DATE NOT NULL,
  quantity_kg DECIMAL(19,6) NOT NULL,
  inventory_value DECIMAL(19,4) NOT NULL,
  profit DECIMAL(19,4) NOT NULL,
  PRIMARY KEY (shop_id, period_from, period_to),
  FOREIGN KEY (shop_id) REFERENCES shops(id)
);
