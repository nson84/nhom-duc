package com.aluminum.manager.auth;

public enum UserRole {
	ADMIN,
	DISTRIBUTION,
	MANUFACTURING;

	public static UserRole from(String value) {
		if (value == null || value.isBlank()) {
			return ADMIN;
		}
		return UserRole.valueOf(value.trim().toUpperCase());
	}

	public boolean isAdmin() {
		return this == ADMIN;
	}

	public boolean canWriteOperations() {
		return this != ADMIN;
	}

	public boolean canAccessPayroll() {
		return this == MANUFACTURING;
	}
}
