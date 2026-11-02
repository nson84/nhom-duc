-- Schema MVP. Flyway chay file nay dung mot lan.
-- Da gom cac rang buoc o muc 4.3: cap so phieu, mot don vi co so,
-- chong ghi trung, dao phieu mot lan, cau hinh cham cong theo ky, chi muc bao cao.

CREATE TABLE product_categories (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uq_category_name (name),
    CONSTRAINT chk_category_name CHECK (CHAR_LENGTH(TRIM(name)) > 0)
) ENGINE=InnoDB;

CREATE TABLE products (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    product_code VARCHAR(50) NOT NULL,
    name VARCHAR(200) NOT NULL,
    category_id BIGINT NOT NULL,
    specification VARCHAR(200),
    base_unit_name VARCHAR(30) NOT NULL,
    reference_purchase_price DECIMAL(19,4) NOT NULL DEFAULT 0,
    default_selling_price DECIMAL(19,4) NOT NULL DEFAULT 0,
    image_path VARCHAR(500),
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uq_product_code (product_code),
    KEY idx_products_category_active (category_id, is_active),
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES product_categories (id),
    CONSTRAINT chk_product_prices CHECK (
        reference_purchase_price >= 0 AND default_selling_price >= 0
    ),
    CONSTRAINT chk_product_names CHECK (
        CHAR_LENGTH(TRIM(product_code)) > 0
        AND CHAR_LENGTH(TRIM(name)) > 0
        AND CHAR_LENGTH(TRIM(base_unit_name)) > 0
    )
) ENGINE=InnoDB;

-- factor_to_base: so don vi co so tuong ung mot don vi nay.
-- base_unit_guard chi nhan product_id khi la don vi co so, de moi san pham chi co mot don vi co so.
CREATE TABLE product_units (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    unit_name VARCHAR(30) NOT NULL,
    factor_to_base DECIMAL(19,8) NOT NULL,
    is_base_unit BOOLEAN NOT NULL DEFAULT FALSE,
    is_default_selling_unit BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    base_unit_guard BIGINT GENERATED ALWAYS AS (
        CASE WHEN is_base_unit = 1 THEN product_id ELSE NULL END
    ) STORED,
    default_selling_guard BIGINT GENERATED ALWAYS AS (
        CASE WHEN is_default_selling_unit = 1 THEN product_id ELSE NULL END
    ) STORED,
    UNIQUE KEY uq_product_unit (product_id, unit_name),
    UNIQUE KEY uq_product_one_base_unit (base_unit_guard),
    UNIQUE KEY uq_product_one_default_selling_unit (default_selling_guard),
    CONSTRAINT fk_product_units_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT chk_unit_factor CHECK (factor_to_base > 0),
    CONSTRAINT chk_base_unit_factor CHECK (is_base_unit = 0 OR factor_to_base = 1),
    CONSTRAINT chk_unit_name CHECK (CHAR_LENGTH(TRIM(unit_name)) > 0)
) ENGINE=InnoDB;

CREATE TABLE stock_balances (
    product_id BIGINT PRIMARY KEY,
    quantity_base DECIMAL(19,6) NOT NULL DEFAULT 0,
    average_cost_per_base DECIMAL(19,6) NOT NULL DEFAULT 0,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_stock_balances_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT chk_stock_quantity CHECK (quantity_base >= 0),
    CONSTRAINT chk_stock_cost CHECK (average_cost_per_base >= 0)
) ENGINE=InnoDB;

CREATE TRIGGER trg_products_stock_balance
AFTER INSERT ON products
FOR EACH ROW
INSERT INTO stock_balances (product_id, quantity_base, average_cost_per_base)
VALUES (NEW.id, 0, 0);

CREATE TRIGGER trg_products_base_unit
AFTER INSERT ON products
FOR EACH ROW
INSERT INTO product_units (
    product_id, unit_name, factor_to_base, is_base_unit, is_default_selling_unit, is_active
) VALUES (NEW.id, NEW.base_unit_name, 1, 1, 1, 1);

-- Cap so phieu trong transaction. Khong ghep chuoi thoi gian.
CREATE TABLE document_sequences (
    document_type VARCHAR(30) NOT NULL,
    period_key CHAR(6) NOT NULL,
    last_number INT NOT NULL,
    PRIMARY KEY (document_type, period_key),
    CONSTRAINT chk_sequence_value CHECK (last_number >= 0)
) ENGINE=InnoDB;

-- document_type: PURCHASE, SALE, ADJUSTMENT_IN, ADJUSTMENT_OUT
-- status: DRAFT, CONFIRMED, CANCELLED
-- reversal_of_document_id: phieu dao tro toi phieu goc. Unique de moi phieu chi bi dao mot lan.
CREATE TABLE stock_documents (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    document_code VARCHAR(50) NOT NULL,
    document_type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    document_date DATETIME(3) NOT NULL,
    counterparty_name VARCHAR(200),
    issued_by VARCHAR(150),
    received_by VARCHAR(150),
    reason VARCHAR(500),
    subtotal DECIMAL(19,4) NOT NULL DEFAULT 0,
    discount_amount DECIMAL(19,4) NOT NULL DEFAULT 0,
    total_amount DECIMAL(19,4) NOT NULL DEFAULT 0,
    payment_status VARCHAR(20) NOT NULL DEFAULT 'UNPAID',
    payment_method VARCHAR(30),
    version INT NOT NULL DEFAULT 0,
    reversal_of_document_id BIGINT,
    idempotency_key VARCHAR(80),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    confirmed_at DATETIME(3),
    cancelled_at DATETIME(3),
    notes TEXT,
    UNIQUE KEY uq_document_code (document_code),
    UNIQUE KEY uq_document_idempotency (idempotency_key),
    UNIQUE KEY uq_document_reversal_once (reversal_of_document_id),
    KEY idx_documents_report (document_type, status, document_date),
    KEY idx_documents_date (document_date),
    CONSTRAINT fk_document_reversal FOREIGN KEY (reversal_of_document_id) REFERENCES stock_documents (id),
    CONSTRAINT chk_document_type CHECK (
        document_type IN ('PURCHASE', 'SALE', 'ADJUSTMENT_IN', 'ADJUSTMENT_OUT')
    ),
    CONSTRAINT chk_document_status CHECK (
        status IN ('DRAFT', 'CONFIRMED', 'CANCELLED')
    ),
    CONSTRAINT chk_document_amounts CHECK (
        subtotal >= 0 AND discount_amount >= 0 AND total_amount >= 0
    ),
    CONSTRAINT chk_payment_status CHECK (
        payment_status IN ('UNPAID', 'PARTIAL', 'PAID')
    )
) ENGINE=InnoDB;

CREATE TABLE stock_document_items (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    document_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    unit_id BIGINT,
    product_code_snapshot VARCHAR(50) NOT NULL,
    product_name_snapshot VARCHAR(200) NOT NULL,
    unit_name_snapshot VARCHAR(30) NOT NULL,
    quantity_document DECIMAL(19,6) NOT NULL,
    factor_to_base_snapshot DECIMAL(19,8) NOT NULL,
    quantity_base DECIMAL(19,6) NOT NULL,
    unit_price DECIMAL(19,4) NOT NULL,
    line_discount DECIMAL(19,4) NOT NULL DEFAULT 0,
    line_total DECIMAL(19,4) NOT NULL,
    cost_per_base_snapshot DECIMAL(19,6),
    cost_total_snapshot DECIMAL(19,4),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_items_document (document_id),
    KEY idx_items_product (product_id),
    CONSTRAINT fk_items_document FOREIGN KEY (document_id) REFERENCES stock_documents (id),
    CONSTRAINT fk_items_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_items_unit FOREIGN KEY (unit_id) REFERENCES product_units (id),
    CONSTRAINT chk_item_quantity CHECK (
        quantity_document > 0 AND quantity_base > 0 AND factor_to_base_snapshot > 0
    ),
    CONSTRAINT chk_item_price CHECK (
        unit_price >= 0 AND line_discount >= 0 AND line_total >= 0
    )
) ENGINE=InnoDB;

CREATE TABLE stock_movements (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    document_id BIGINT NOT NULL,
    document_item_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    movement_type VARCHAR(20) NOT NULL,
    quantity_delta_base DECIMAL(19,6) NOT NULL,
    unit_cost DECIMAL(19,6) NOT NULL DEFAULT 0,
    movement_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uq_movement_item (document_item_id),
    KEY idx_movement_product_date (product_id, movement_at),
    KEY idx_movement_document (document_id),
    CONSTRAINT fk_movement_document FOREIGN KEY (document_id) REFERENCES stock_documents (id),
    CONSTRAINT fk_movement_item FOREIGN KEY (document_item_id) REFERENCES stock_document_items (id),
    CONSTRAINT fk_movement_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT chk_movement_type CHECK (movement_type IN ('IN', 'OUT')),
    CONSTRAINT chk_movement_delta CHECK (quantity_delta_base <> 0)
) ENGINE=InnoDB;

CREATE TABLE employees (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    employee_code VARCHAR(50) NOT NULL,
    full_name VARCHAR(150) NOT NULL,
    phone VARCHAR(30),
    address VARCHAR(300),
    hire_date DATE,
    termination_date DATE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    notes TEXT,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uq_employee_code (employee_code),
    KEY idx_employees_active (is_active),
    CONSTRAINT chk_employee_name CHECK (CHAR_LENGTH(TRIM(full_name)) > 0)
) ENGINE=InnoDB;

CREATE TABLE employee_pay_rates (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    employee_id BIGINT NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE,
    daily_rate DECIMAL(19,4) NOT NULL DEFAULT 0,
    standard_hours_per_day DECIMAL(8,2) NOT NULL DEFAULT 8,
    overtime_weekday_rate DECIMAL(19,4) NOT NULL DEFAULT 0,
    overtime_restday_rate DECIMAL(19,4) NOT NULL DEFAULT 0,
    overtime_holiday_rate DECIMAL(19,4) NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_pay_rate_employee_date (employee_id, effective_from),
    CONSTRAINT fk_pay_rate_employee FOREIGN KEY (employee_id) REFERENCES employees (id),
    CONSTRAINT chk_pay_rate_dates CHECK (
        effective_to IS NULL OR effective_to >= effective_from
    ),
    CONSTRAINT chk_pay_rate_amounts CHECK (
        daily_rate >= 0 AND standard_hours_per_day > 0
        AND overtime_weekday_rate >= 0
        AND overtime_restday_rate >= 0
        AND overtime_holiday_rate >= 0
    )
) ENGINE=InnoDB;

CREATE TABLE holidays (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    holiday_date DATE NOT NULL,
    name VARCHAR(150) NOT NULL,
    holiday_type VARCHAR(30) NOT NULL DEFAULT 'PUBLIC_HOLIDAY',
    is_paid BOOLEAN NOT NULL DEFAULT TRUE,
    notes VARCHAR(500),
    UNIQUE KEY uq_holiday_date_name (holiday_date, name),
    CONSTRAINT chk_holiday_type CHECK (
        holiday_type IN ('PUBLIC_HOLIDAY', 'COMPANY_DAY_OFF')
    )
) ENGINE=InnoDB;

-- Quy tac cham cong theo thoi diem ap dung. Ky da chot giu snapshot, khong doc lai cau hinh moi.
CREATE TABLE attendance_policies (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    effective_from DATE NOT NULL,
    effective_to DATE,
    standard_start_time TIME NOT NULL DEFAULT '08:00:00',
    standard_end_time TIME NOT NULL DEFAULT '17:00:00',
    break_minutes INT NOT NULL DEFAULT 60,
    late_grace_minutes INT NOT NULL DEFAULT 0,
    early_leave_grace_minutes INT NOT NULL DEFAULT 0,
    rounding_minutes INT NOT NULL DEFAULT 1,
    half_day_threshold_hours DECIMAL(8,2) NOT NULL DEFAULT 4,
    full_day_credited_days DECIMAL(5,2) NOT NULL DEFAULT 1,
    half_day_credited_days DECIMAL(5,2) NOT NULL DEFAULT 0.5,
    overnight_shift_allowed BOOLEAN NOT NULL DEFAULT TRUE,
    notes VARCHAR(500),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_attendance_policy_from (effective_from),
    CONSTRAINT chk_attendance_policy_dates CHECK (
        effective_to IS NULL OR effective_to >= effective_from
    ),
    CONSTRAINT chk_attendance_policy_values CHECK (
        break_minutes >= 0
        AND late_grace_minutes >= 0
        AND early_leave_grace_minutes >= 0
        AND rounding_minutes >= 1
        AND half_day_threshold_hours >= 0
        AND full_day_credited_days >= 0
        AND half_day_credited_days >= 0
    )
) ENGINE=InnoDB;

CREATE TABLE attendance_records (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    employee_id BIGINT NOT NULL,
    work_date DATE NOT NULL,
    attendance_status VARCHAR(30) NOT NULL DEFAULT 'UNMARKED',
    clock_in DATETIME(3),
    clock_out DATETIME(3),
    worked_hours DECIMAL(8,3) NOT NULL DEFAULT 0,
    credited_days DECIMAL(5,2) NOT NULL DEFAULT 0,
    late_minutes INT NOT NULL DEFAULT 0,
    early_leave_minutes INT NOT NULL DEFAULT 0,
    notes VARCHAR(500),
    pay_rate_id BIGINT,
    daily_rate_snapshot DECIMAL(19,4) NOT NULL DEFAULT 0,
    is_locked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uq_attendance_employee_date (employee_id, work_date),
    KEY idx_attendance_date (work_date),
    CONSTRAINT fk_attendance_employee FOREIGN KEY (employee_id) REFERENCES employees (id),
    CONSTRAINT fk_attendance_pay_rate FOREIGN KEY (pay_rate_id) REFERENCES employee_pay_rates (id),
    CONSTRAINT chk_attendance_status CHECK (
        attendance_status IN (
            'UNMARKED', 'PRESENT', 'HALF_DAY', 'ABSENT',
            'PAID_LEAVE', 'UNPAID_LEAVE', 'HOLIDAY'
        )
    ),
    CONSTRAINT chk_attendance_hours CHECK (
        worked_hours >= 0 AND credited_days >= 0
        AND late_minutes >= 0 AND early_leave_minutes >= 0
    )
) ENGINE=InnoDB;

CREATE TABLE overtime_records (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    attendance_id BIGINT NOT NULL,
    overtime_type VARCHAR(20) NOT NULL,
    start_at DATETIME(3),
    end_at DATETIME(3),
    overtime_hours DECIMAL(8,3) NOT NULL,
    hourly_rate_snapshot DECIMAL(19,4) NOT NULL,
    amount DECIMAL(19,4) NOT NULL,
    is_approved BOOLEAN NOT NULL DEFAULT FALSE,
    notes VARCHAR(500),
    KEY idx_overtime_attendance (attendance_id),
    CONSTRAINT fk_overtime_attendance FOREIGN KEY (attendance_id) REFERENCES attendance_records (id),
    CONSTRAINT chk_overtime_type CHECK (
        overtime_type IN ('WEEKDAY', 'RESTDAY', 'HOLIDAY')
    ),
    CONSTRAINT chk_overtime_amount CHECK (
        overtime_hours >= 0 AND hourly_rate_snapshot >= 0 AND amount >= 0
    )
) ENGINE=InnoDB;

CREATE TABLE payroll_periods (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    period_year SMALLINT NOT NULL,
    period_month TINYINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    closed_at DATETIME(3),
    closed_by_admin_id BIGINT,
    reopened_at DATETIME(3),
    reopened_by_admin_id BIGINT,
    reopen_reason VARCHAR(500),
    notes VARCHAR(500),
    UNIQUE KEY uq_payroll_period (period_year, period_month),
    CONSTRAINT chk_payroll_month CHECK (period_month BETWEEN 1 AND 12),
    CONSTRAINT chk_payroll_status CHECK (status IN ('OPEN', 'CLOSED'))
) ENGINE=InnoDB;

CREATE TABLE payroll_details (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    payroll_period_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    credited_days DECIMAL(8,3) NOT NULL DEFAULT 0,
    base_pay DECIMAL(19,4) NOT NULL DEFAULT 0,
    overtime_pay DECIMAL(19,4) NOT NULL DEFAULT 0,
    allowances DECIMAL(19,4) NOT NULL DEFAULT 0,
    deductions DECIMAL(19,4) NOT NULL DEFAULT 0,
    total_pay DECIMAL(19,4) NOT NULL DEFAULT 0,
    calculation_snapshot JSON,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uq_payroll_employee (payroll_period_id, employee_id),
    CONSTRAINT fk_payroll_detail_period FOREIGN KEY (payroll_period_id) REFERENCES payroll_periods (id),
    CONSTRAINT fk_payroll_detail_employee FOREIGN KEY (employee_id) REFERENCES employees (id),
    CONSTRAINT chk_payroll_components CHECK (
        credited_days >= 0 AND base_pay >= 0 AND overtime_pay >= 0
        AND allowances >= 0 AND deductions >= 0
    ),
    CONSTRAINT chk_payroll_total CHECK (
        total_pay = base_pay + overtime_pay + allowances - deductions
    )
) ENGINE=InnoDB;

-- category: SALE_RECEIPT, PURCHASE_PAYMENT, OPERATING_EXPENSE, PAYROLL, OTHER_INCOME, OTHER_EXPENSE
CREATE TABLE financial_transactions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    transaction_code VARCHAR(50) NOT NULL,
    transaction_date DATETIME(3) NOT NULL,
    transaction_type VARCHAR(10) NOT NULL,
    category VARCHAR(30) NOT NULL,
    amount DECIMAL(19,4) NOT NULL,
    description VARCHAR(500),
    reference_type VARCHAR(30),
    reference_id BIGINT,
    status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED',
    idempotency_key VARCHAR(80),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uq_finance_code (transaction_code),
    UNIQUE KEY uq_finance_idempotency (idempotency_key),
    KEY idx_finance_date_type (transaction_date, transaction_type),
    KEY idx_finance_reference (reference_type, reference_id),
    CONSTRAINT chk_finance_type CHECK (transaction_type IN ('INCOME', 'EXPENSE')),
    CONSTRAINT chk_finance_amount CHECK (amount > 0),
    CONSTRAINT chk_finance_status CHECK (status IN ('CONFIRMED', 'VOID')),
    CONSTRAINT chk_finance_category_type CHECK (
        (transaction_type = 'INCOME' AND category IN ('SALE_RECEIPT', 'OTHER_INCOME'))
        OR
        (transaction_type = 'EXPENSE' AND category IN (
            'PURCHASE_PAYMENT', 'OPERATING_EXPENSE', 'PAYROLL', 'OTHER_EXPENSE'
        ))
    )
) ENGINE=InnoDB;

CREATE TABLE sales_payments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    document_id BIGINT NOT NULL,
    payment_date DATETIME(3) NOT NULL,
    amount DECIMAL(19,4) NOT NULL,
    payment_method VARCHAR(30) NOT NULL,
    note VARCHAR(500),
    financial_transaction_id BIGINT,
    idempotency_key VARCHAR(80),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uq_payment_finance (financial_transaction_id),
    UNIQUE KEY uq_payment_idempotency (idempotency_key),
    KEY idx_payments_document (document_id),
    KEY idx_payments_date (payment_date),
    CONSTRAINT fk_payment_document FOREIGN KEY (document_id) REFERENCES stock_documents (id),
    CONSTRAINT fk_payment_finance FOREIGN KEY (financial_transaction_id) REFERENCES financial_transactions (id),
    CONSTRAINT chk_payment_amount CHECK (amount > 0)
) ENGINE=InnoDB;

CREATE TABLE admin_users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(80) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    must_change_password BOOLEAN NOT NULL DEFAULT TRUE,
    last_login_at DATETIME(3),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uq_admin_username (username)
) ENGINE=InnoDB;

ALTER TABLE payroll_periods
    ADD CONSTRAINT fk_payroll_closed_by FOREIGN KEY (closed_by_admin_id) REFERENCES admin_users (id),
    ADD CONSTRAINT fk_payroll_reopened_by FOREIGN KEY (reopened_by_admin_id) REFERENCES admin_users (id);

CREATE TABLE app_settings (
    setting_key VARCHAR(100) PRIMARY KEY,
    setting_value TEXT,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB;

CREATE TABLE audit_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    admin_user_id BIGINT,
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id VARCHAR(100),
    description TEXT,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_audit_date (created_at),
    CONSTRAINT fk_audit_admin FOREIGN KEY (admin_user_id) REFERENCES admin_users (id) ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE backup_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    backup_path VARCHAR(1000),
    started_at DATETIME(3) NOT NULL,
    completed_at DATETIME(3),
    status VARCHAR(20) NOT NULL,
    file_size_bytes BIGINT,
    checksum_sha256 VARCHAR(64),
    error_message TEXT,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_backup_started (started_at),
    CONSTRAINT chk_backup_status CHECK (
        status IN ('RUNNING', 'SUCCESS', 'FAILED')
    )
) ENGINE=InnoDB;

INSERT INTO app_settings (setting_key, setting_value) VALUES
    ('shop.name', 'Cửa hàng nhôm'),
    ('shop.address', ''),
    ('shop.phone', ''),
    ('backup.time', '22:00'),
    ('backup.retention_days', '30'),
    ('backup.directory', ''),
    ('timezone', 'Asia/Ho_Chi_Minh');

INSERT INTO attendance_policies (
    effective_from,
    standard_start_time,
    standard_end_time,
    break_minutes,
    late_grace_minutes,
    early_leave_grace_minutes,
    rounding_minutes,
    half_day_threshold_hours,
    full_day_credited_days,
    half_day_credited_days,
    overnight_shift_allowed,
    notes
) VALUES (
    '2026-01-01',
    '08:00:00',
    '17:00:00',
    60,
    0,
    0,
    1,
    4.00,
    1.00,
    0.50,
    1,
    'Cấu hình mặc định. Kỳ công đã chốt giữ số liệu riêng, không đọc lại cấu hình mới.'
);
