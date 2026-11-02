package com.aluminum.manager.system;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "app_settings")
public class AppSetting {

	@Id
	@Column(name = "setting_key", length = 100)
	private String settingKey;

	@Column(name = "setting_value", columnDefinition = "TEXT")
	private String settingValue;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	protected AppSetting() {
	}

	public AppSetting(String settingKey, String settingValue) {
		this.settingKey = settingKey;
		this.settingValue = settingValue;
	}

	@PrePersist
	@PreUpdate
	void touch() {
		updatedAt = LocalDateTime.now();
	}

	public String getSettingKey() {
		return settingKey;
	}

	public String getSettingValue() {
		return settingValue;
	}

	public void setSettingValue(String settingValue) {
		this.settingValue = settingValue;
	}
}
