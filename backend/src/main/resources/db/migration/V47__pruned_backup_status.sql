ALTER TABLE backup_logs DROP CHECK chk_backup_status;
ALTER TABLE backup_logs ADD CONSTRAINT chk_backup_status
    CHECK (status IN ('RUNNING', 'SUCCESS', 'FAILED', 'PRUNED'));
