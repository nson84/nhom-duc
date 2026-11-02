-- Chay mot lan bang tai khoan quan tri MySQL tren may cuc bo.
-- Mat khau duoi day chi dung cho may phat trien. Bo cai that se sinh mat khau rieng.

CREATE DATABASE IF NOT EXISTS aluminum_app
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'aluminum_app'@'localhost' IDENTIFIED BY 'aluminum_app_dev';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES, TRIGGER
  ON aluminum_app.* TO 'aluminum_app'@'localhost';
FLUSH PRIVILEGES;
