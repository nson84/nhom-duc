package com.aluminum.manager.system;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
public class AuditLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "admin_user_id")
	private Long adminUserId;

	@Column(nullable = false, length = 100)
	private String action;

	@Column(name = "entity_type", nullable = false, length = 100)
	private String entityType;

	@Column(name = "entity_id", length = 100)
	private String entityId;

	@Column(columnDefinition = "TEXT")
	private String description;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	protected AuditLog() {
	}

	public AuditLog(Long adminUserId, String action, String entityType, String entityId, String description) {
		this.adminUserId = adminUserId;
		this.action = action;
		this.entityType = entityType;
		this.entityId = entityId;
		this.description = description;
	}

	@PrePersist
	void prePersist() {
		if (createdAt == null) {
			createdAt = LocalDateTime.now();
		}
	}
}
