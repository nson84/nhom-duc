-- Landing page cong khai: thu thong tin khach de tu van.
--
-- Khach vao trang gioi thieu, dien form lien he, thong tin roi vao bang nay.
-- Chi ADMIN (dang nhap app quan ly) moi doc duoc danh sach; API tao lead la cong khai.
--
-- status: NEW (moi, chua goi), CONTACTED (da goi), CLOSED (da xu ly xong)
-- Khong luu gi nhay cam ngoai thong tin khach tu nguyen dien.

CREATE TABLE customer_leads (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    full_name VARCHAR(150) NOT NULL,
    phone VARCHAR(30) NOT NULL,
    email VARCHAR(150),
    message VARCHAR(1000),
    -- Nhu cau khach chon tren form: BAO_GIA, TU_VAN_KY_THUAT, DAT_HANG, KHAC
    request_type VARCHAR(30) NOT NULL DEFAULT 'BAO_GIA',
    status VARCHAR(20) NOT NULL DEFAULT 'NEW',
    -- Ghi chu cua nguoi goi lai (khong hien ra ngoai)
    internal_note VARCHAR(1000),
    contacted_at DATETIME(3),
    contacted_by_admin_id BIGINT,
    -- Dau vet phuc vu chong spam, khong dung cho nghiep vu
    source_page VARCHAR(200),
    ip_address VARCHAR(64),
    user_agent VARCHAR(300),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    KEY idx_leads_status_created (status, created_at),
    KEY idx_leads_phone (phone),
    KEY idx_leads_created (created_at),
    CONSTRAINT fk_leads_contacted_by FOREIGN KEY (contacted_by_admin_id) REFERENCES admin_users (id),
    CONSTRAINT chk_leads_status CHECK (status IN ('NEW', 'CONTACTED', 'CLOSED')),
    CONSTRAINT chk_leads_request_type CHECK (
        request_type IN ('BAO_GIA', 'TU_VAN_KY_THUAT', 'DAT_HANG', 'KHAC')
    ),
    CONSTRAINT chk_leads_name CHECK (CHAR_LENGTH(TRIM(full_name)) > 0),
    CONSTRAINT chk_leads_phone CHECK (CHAR_LENGTH(TRIM(phone)) > 0)
) ENGINE=InnoDB;
