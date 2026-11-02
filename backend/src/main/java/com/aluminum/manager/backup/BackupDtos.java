package com.aluminum.manager.backup;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public final class BackupDtos {

	private BackupDtos() {
	}

	public record BackupLogDto(
			Long id,
			String backupPath,
			LocalDateTime startedAt,
			LocalDateTime completedAt,
			String status,
			Long fileSizeBytes,
			String checksumSha256,
			String errorMessage,
			LocalDateTime createdAt
	) {
	}

	public record BackupStatusDto(
			BackupLogDto latestBackup,
			boolean lastBackupFailed,
			String warningMessage,
			String backupTime,
			int retentionDays,
			String effectiveDirectory
	) {
	}

	public record BackupSettingsDto(
			@NotBlank(message = "Nhập giờ sao lưu")
			@Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "Giờ sao lưu theo dạng HH:mm")
			String backupTime,
			@Min(value = 1, message = "Số ngày giữ tối thiểu là 1")
			@Max(value = 3650, message = "Số ngày giữ tối đa là 3650")
			int backupRetentionDays,
			@Size(max = 1000)
			String backupDirectory
	) {
	}

	public record RestoreRequest(
			@NotBlank(message = "Nhập chữ xác nhận KHOI PHUC") String confirmText,
			@NotBlank(message = "Nhập lý do phục hồi dữ liệu") @Size(max = 500) String reason
	) {
	}
}
