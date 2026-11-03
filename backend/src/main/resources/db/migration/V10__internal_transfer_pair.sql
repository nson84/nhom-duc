-- Liên kết phiếu bán nội bộ (phân phối) với phiếu nhập nội bộ tự tạo (xưởng).
ALTER TABLE stock_documents
    ADD COLUMN internal_pair_document_id BIGINT NULL,
    ADD UNIQUE KEY uq_document_internal_pair (internal_pair_document_id),
    ADD CONSTRAINT fk_document_internal_pair
        FOREIGN KEY (internal_pair_document_id) REFERENCES stock_documents (id);
