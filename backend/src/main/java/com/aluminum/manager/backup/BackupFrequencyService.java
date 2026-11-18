package com.aluminum.manager.backup;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
class BackupFrequencyService {
	private final JdbcTemplate jdbc;
	BackupFrequencyService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

	int get() {
		String value = jdbc.query("SELECT setting_value FROM app_settings WHERE setting_key = 'backup.frequency_hours'",
				rs -> rs.next() ? rs.getString(1) : "4");
		try { return allowed(Integer.parseInt(value)) ? Integer.parseInt(value) : 4; }
		catch (NumberFormatException e) { return 4; }
	}

	void set(int hours) {
		if (!allowed(hours)) throw new IllegalArgumentException("Chọn tần suất 1, 2, 4, 8 hoặc 24 giờ");
		jdbc.update("INSERT INTO app_settings (setting_key, setting_value) VALUES ('backup.frequency_hours', ?) "
				+ "ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value)", Integer.toString(hours));
	}

	private static boolean allowed(int value) { return value == 1 || value == 2 || value == 4 || value == 8 || value == 24; }
}
