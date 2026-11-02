package com.aluminum.manager.system;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

	private final AuditLogRepository auditLogs;

	public AuditService(AuditLogRepository auditLogs) {
		this.auditLogs = auditLogs;
	}

	@Transactional
	public void record(Long adminUserId, String action, String entityType, String entityId, String description) {
		auditLogs.save(new AuditLog(adminUserId, action, entityType, entityId, description));
	}
}
