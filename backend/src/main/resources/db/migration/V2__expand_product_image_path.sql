-- Cho phep luu anh san pham truc tiep duoi dang data URL (base64) hoac duong dan dai
ALTER TABLE products
    MODIFY COLUMN image_path MEDIUMTEXT;
