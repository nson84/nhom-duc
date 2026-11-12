-- Thùng rác phiếu nháp / hủy: giữ 90 ngày rồi xóa cứng (nếu không còn ràng buộc kho).

ALTER TABLE stock_documents
    ADD COLUMN trashed_at DATETIME(3) NULL AFTER cancelled_at,
    ADD KEY idx_documents_trashed (trashed_at);
