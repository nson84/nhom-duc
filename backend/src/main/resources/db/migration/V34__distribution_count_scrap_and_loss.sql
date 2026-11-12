-- Thống kê phế liệu và hao hụt riêng trên lần kiểm kê mới nhất của từng sản phẩm / loại.
-- Đây là bản chụp lần cân gần nhất, không phải tổng cộng dồn các kỳ lịch sử.
ALTER TABLE distribution_inventory_count_evidence
  ADD COLUMN scrap_quantity_kg DECIMAL(19,6) NOT NULL DEFAULT 0,
  ADD COLUMN loss_quantity_kg DECIMAL(19,6) NOT NULL DEFAULT 0,
  ADD CONSTRAINT chk_dist_scrap_nonnegative CHECK (scrap_quantity_kg >= 0),
  ADD CONSTRAINT chk_dist_loss_nonnegative CHECK (loss_quantity_kg >= 0);
