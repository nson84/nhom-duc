package com.aluminum.manager.system;

import com.aluminum.manager.common.NameValidation;
import com.aluminum.manager.security.CurrentAdmin;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettingsService {

	static final String SHOP_NAME = "shop.name";
	static final String SHOP_ADDRESS = "shop.address";
	static final String SHOP_PHONE = "shop.phone";
	static final String BACKUP_TIME = "backup.time";
	static final String BACKUP_RETENTION_DAYS = "backup.retention_days";
	static final String BACKUP_DIRECTORY = "backup.directory";

	private final AppSettingRepository settings;
	private final AuditService auditService;

	public SettingsService(AppSettingRepository settings, AuditService auditService) {
		this.settings = settings;
		this.auditService = auditService;
	}

	@Transactional(readOnly = true)
	public ShopSettings get() {
		return new ShopSettings(
				value(SHOP_NAME, "Cửa hàng nhôm"),
				value(SHOP_ADDRESS, ""),
				value(SHOP_PHONE, ""),
				value(BACKUP_TIME, "22:00"),
				Integer.valueOf(value(BACKUP_RETENTION_DAYS, "30")),
				value(BACKUP_DIRECTORY, ""));
	}

	@Transactional
	public ShopSettings update(ShopSettings request) {
		NameValidation.requireNoLeadingDigit("Tên cửa hàng", request.shopName());
		save(SHOP_NAME, request.shopName().trim());
		save(SHOP_ADDRESS, blankToEmpty(request.shopAddress()));
		save(SHOP_PHONE, blankToEmpty(request.shopPhone()));
		save(BACKUP_TIME, request.backupTime());
		save(BACKUP_RETENTION_DAYS, String.valueOf(request.backupRetentionDays()));
		save(BACKUP_DIRECTORY, blankToEmpty(request.backupDirectory()));
		var admin = CurrentAdmin.get();
		auditService.record(admin.id(), "UPDATE_SETTINGS", "app_settings", null, "Cập nhật cấu hình cửa hàng");
		return get();
	}

	private void save(String key, String value) {
		AppSetting setting = settings.findById(key).orElseGet(() -> new AppSetting(key, value));
		setting.setSettingValue(value);
		settings.save(setting);
	}

	private String value(String key, String fallback) {
		return settings.findById(key).map(AppSetting::getSettingValue).orElse(fallback);
	}

	private static String blankToEmpty(String value) {
		return value == null ? "" : value.trim();
	}
}
