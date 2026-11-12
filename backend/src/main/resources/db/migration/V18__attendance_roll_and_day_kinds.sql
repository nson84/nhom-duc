-- Ba loại ngày nghỉ, mỗi ngày một mức lương. Phiếu điểm danh khóa sau khi lưu.
ALTER TABLE holidays
    ADD COLUMN day_pay DECIMAL(19,4) NOT NULL DEFAULT 0 AFTER is_paid;

UPDATE holidays SET holiday_type = 'NATIONAL_HOLIDAY' WHERE holiday_type = 'PUBLIC_HOLIDAY';
UPDATE holidays SET holiday_type = 'SHOP_CLOSED' WHERE holiday_type = 'COMPANY_DAY_OFF';

ALTER TABLE holidays DROP CHECK chk_holiday_type;
ALTER TABLE holidays
    ADD CONSTRAINT chk_holiday_type CHECK (
        holiday_type IN ('SHOP_CLOSED', 'NATIONAL_HOLIDAY', 'CATHOLIC_HOLIDAY')
    ),
    ADD CONSTRAINT chk_holiday_day_pay CHECK (day_pay >= 0);

CREATE TABLE attendance_day_locks (
    work_date DATE PRIMARY KEY,
    locked_at DATETIME(3) NOT NULL,
    locked_by_admin_id BIGINT NULL,
    CONSTRAINT fk_day_lock_admin FOREIGN KEY (locked_by_admin_id) REFERENCES admin_users (id)
) ENGINE=InnoDB;
