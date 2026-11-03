-- Ba tài khoản vận hành: admin (xem báo cáo), phanphoi, xuong.
ALTER TABLE admin_users
    ADD COLUMN role VARCHAR(30) NOT NULL DEFAULT 'ADMIN' AFTER username,
    ADD COLUMN shop_code VARCHAR(30) NULL AFTER role;

UPDATE admin_users SET role = 'ADMIN', shop_code = NULL;

ALTER TABLE admin_users
    ADD CONSTRAINT chk_admin_role CHECK (role IN ('ADMIN', 'DISTRIBUTION', 'MANUFACTURING')),
    ADD CONSTRAINT chk_admin_shop_code CHECK (
        (role = 'ADMIN' AND shop_code IS NULL)
        OR (role = 'DISTRIBUTION' AND shop_code = 'DISTRIBUTION')
        OR (role = 'MANUFACTURING' AND shop_code = 'MANUFACTURING')
    );
