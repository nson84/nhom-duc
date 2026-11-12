-- Nhiều tài khoản nhận VietQR (ảnh thẻ QR + số TK). Tài khoản mặc định dùng khi in danh sách nợ.
CREATE TABLE bank_receiving_accounts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    label VARCHAR(100) NOT NULL,
    bank_bin VARCHAR(6) NOT NULL,
    account_no VARCHAR(19) NOT NULL,
    account_name VARCHAR(100) NOT NULL DEFAULT '',
    image_path MEDIUMTEXT NULL,
    is_default TINYINT(1) NOT NULL DEFAULT 0,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_bank_account_no_bin (bank_bin, account_no)
);

-- Chuyển cấu hình bank.* cũ (một tài khoản) sang bảng mới nếu có.
INSERT INTO bank_receiving_accounts (label, bank_bin, account_no, account_name, is_default, sort_order)
SELECT
    'Tài khoản mặc định',
    TRIM(bin.setting_value),
    TRIM(acc.setting_value),
    COALESCE(TRIM(name.setting_value), ''),
    1,
    0
FROM app_settings bin
JOIN app_settings acc ON acc.setting_key = 'bank.account_no'
LEFT JOIN app_settings name ON name.setting_key = 'bank.account_name'
WHERE bin.setting_key = 'bank.bin'
  AND TRIM(bin.setting_value) <> ''
  AND TRIM(acc.setting_value) <> '';

DELETE FROM app_settings WHERE setting_key IN ('bank.bin', 'bank.account_no', 'bank.account_name');
