package com.aluminum.manager.backup;

import com.aluminum.manager.backup.BackupDtos.BackupLogDto;
import com.aluminum.manager.backup.BackupDtos.BackupSettingsDto;
import com.aluminum.manager.backup.BackupDtos.BackupStatusDto;
import com.aluminum.manager.backup.BackupDtos.RestoreRequest;
import com.aluminum.manager.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/backups")
public class BackupController {

	private final BackupService service;

	public BackupController(BackupService service) {
		this.service = service;
	}

	@GetMapping
	public ApiResponse<List<BackupLogDto>> list() {
		return ApiResponse.ok(service.listBackups(), null);
	}

	@PostMapping
	public ApiResponse<BackupLogDto> create() {
		return ApiResponse.ok(service.createManualBackup(), "Đã sao lưu dữ liệu thành công");
	}

	@GetMapping("/status")
	public ApiResponse<BackupStatusDto> status() {
		return ApiResponse.ok(service.status(), null);
	}

	@PostMapping("/{id}/restore")
	public ApiResponse<BackupLogDto> restore(
			@PathVariable long id, @Valid @RequestBody RestoreRequest request) {
		return ApiResponse.ok(service.restoreBackup(id, request), "Đã phục hồi dữ liệu thành công");
	}

	@GetMapping("/settings")
	public ApiResponse<BackupSettingsDto> getSettings() {
		return ApiResponse.ok(service.getSettings(), null);
	}

	@PutMapping("/settings")
	public ApiResponse<BackupSettingsDto> updateSettings(@Valid @RequestBody BackupSettingsDto request) {
		return ApiResponse.ok(service.updateSettings(request), "Đã lưu cấu hình lịch sao lưu");
	}
}
