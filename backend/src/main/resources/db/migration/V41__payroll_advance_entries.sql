CREATE TABLE payroll_advance_entries (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    payroll_detail_id BIGINT NOT NULL,
    advance_date DATE NOT NULL,
    amount DECIMAL(19,4) NOT NULL,
    note VARCHAR(500),
    idempotency_key VARCHAR(80) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uq_payroll_advance_key (idempotency_key),
    KEY idx_payroll_advance_detail (payroll_detail_id, advance_date),
    CONSTRAINT fk_payroll_advance_detail FOREIGN KEY (payroll_detail_id) REFERENCES payroll_details(id),
    CONSTRAINT chk_payroll_advance_amount CHECK (amount > 0)
) ENGINE=InnoDB;
