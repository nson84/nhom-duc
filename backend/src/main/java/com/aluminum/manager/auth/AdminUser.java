package com.aluminum.manager.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "admin_users")
public class AdminUser {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 80)
	private String username;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private UserRole role = UserRole.ADMIN;

	@Column(name = "shop_code", length = 30)
	private String shopCode;

	@Column(name = "password_hash", nullable = false, length = 255)
	private String passwordHash;

	@Column(name = "is_active", nullable = false)
	private boolean active = true;

	@Column(name = "must_change_password", nullable = false)
	private boolean mustChangePassword = true;

	@Column(name = "last_login_at")
	private LocalDateTime lastLoginAt;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	protected AdminUser() {
	}

	public AdminUser(String username, String passwordHash, UserRole role, String shopCode) {
		this.username = username;
		this.passwordHash = passwordHash;
		this.role = role;
		this.shopCode = shopCode;
	}

	@PrePersist
	void prePersist() {
		if (createdAt == null) {
			createdAt = LocalDateTime.now();
		}
	}

	public Long getId() {
		return id;
	}

	public String getUsername() {
		return username;
	}

	public UserRole getRole() {
		return role == null ? UserRole.ADMIN : role;
	}

	public void setRole(UserRole role) {
		this.role = role;
	}

	public String getShopCode() {
		return shopCode;
	}

	public void setShopCode(String shopCode) {
		this.shopCode = shopCode;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public void setPasswordHash(String passwordHash) {
		this.passwordHash = passwordHash;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public boolean isMustChangePassword() {
		return mustChangePassword;
	}

	public void setMustChangePassword(boolean mustChangePassword) {
		this.mustChangePassword = mustChangePassword;
	}

	public void setLastLoginAt(LocalDateTime lastLoginAt) {
		this.lastLoginAt = lastLoginAt;
	}
}
