-- Người thu gắn vào lần ghi thu, giữ tên tài khoản tại thời điểm thu.
ALTER TABLE sales_payments
    ADD COLUMN collected_by_name VARCHAR(80) NULL AFTER note;

-- Phiếu đã thu trước đó: ghép lần thu với nhật ký RECEIVE_PAYMENT theo thứ tự ghi.
WITH pay AS (
    SELECT id, document_id,
           ROW_NUMBER() OVER (PARTITION BY document_id ORDER BY id) AS rn
    FROM sales_payments
),
aud AS (
    SELECT a.entity_id, u.username,
           ROW_NUMBER() OVER (PARTITION BY a.entity_id ORDER BY a.id) AS rn
    FROM audit_logs a
    JOIN admin_users u ON u.id = a.admin_user_id
    WHERE a.action = 'RECEIVE_PAYMENT'
      AND a.entity_type = 'stock_documents'
)
UPDATE sales_payments sp
JOIN pay ON pay.id = sp.id
JOIN aud ON aud.entity_id = CAST(pay.document_id AS CHAR) AND aud.rn = pay.rn
SET sp.collected_by_name = aud.username;
