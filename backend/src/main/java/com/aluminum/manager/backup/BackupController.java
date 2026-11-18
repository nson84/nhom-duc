package com.aluminum.manager.backup;

import com.aluminum.manager.backup.BackupDtos.BackupLogDto;
import com.aluminum.manager.backup.BackupDtos.BackupSettingsDto;
import com.aluminum.manager.backup.BackupDtos.BackupStatusDto;
import com.aluminum.manager.backup.BackupDtos.RestoreRequest;
import com.aluminum.manager.common.ApiResponse;
import com.aluminum.manager.common.BusinessException;
import com.aluminum.manager.common.ErrorCode;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/backups")
public class BackupController {

	private final BackupService service;
	private final R2CloudService cloud;
	private final CloudBackupService cloudBackups;
	private final RecoveryKeyService recoveryKey;
	private final StagedRestoreService stagedRestore;

	public BackupController(BackupService service, R2CloudService cloud,
			CloudBackupService cloudBackups, RecoveryKeyService recoveryKey,
			StagedRestoreService stagedRestore) {
		this.service = service;
		this.cloud = cloud;
		this.cloudBackups = cloudBackups;
		this.recoveryKey = recoveryKey;
		this.stagedRestore = stagedRestore;
	}

	@GetMapping("/recovery-key")
	public ApiResponse<String> recoveryKey(HttpServletResponse response) {
		response.setHeader("Cache-Control", "no-store, no-cache, max-age=0");
		response.setHeader("Pragma", "no-cache");
		return ApiResponse.ok(recoveryKey.exportKey(), "Lưu khóa này bên ngoài máy tính để có thể khôi phục khi mất máy");
	}

	@PostMapping("/recovery-key/import")
	public ApiResponse<String> importRecoveryKey(@RequestBody RecoveryKeyInput request) {
		try { recoveryKey.importKey(request.value()); }
		catch (IllegalArgumentException | IllegalStateException e) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, e.getMessage(), null);
		}
		return ApiResponse.ok("IMPORTED", "Đã lưu khóa phục hồi trên máy này");
	}

	public record RecoveryKeyInput(String value) {}

	@GetMapping("/cloud/status")
	public ApiResponse<CloudBackupService.CloudStatus> cloudStatus() {
		return ApiResponse.ok(cloudBackups.status(), null);
	}

	@GetMapping("/cloud/objects")
	public ApiResponse<List<CloudBackupService.CloudObject>> cloudObjects() {
		return ApiResponse.ok(cloudBackups.list(), null);
	}

	@PostMapping("/cloud/sync")
	public ApiResponse<Integer> syncCloud() {
		return ApiResponse.ok(cloudBackups.syncPending(), "Đã thử đồng bộ các bản sao lưu đang chờ");
	}

	@PostMapping("/cloud/download")
	public ApiResponse<String> downloadCloud(@RequestParam String key) throws IOException {
		return ApiResponse.ok(cloudBackups.download(key).toString(), "Đã tải bản sao lưu về máy");
	}

	@PostMapping("/cloud/verify")
	public ApiResponse<String> verifyCloud(@RequestBody CloudVerifyRequest request) throws IOException {
		if (request.recoveryKey() != null && !request.recoveryKey().isBlank()) {
			try {
				RecoveryKeyService.parse(request.recoveryKey());
			} catch (IllegalArgumentException e) {
				throw new BusinessException(ErrorCode.VALIDATION_ERROR,
						"Khóa phục hồi không hợp lệ. Trên máy đã tạo bản sao, hãy để trống ô này để dùng khóa đã lưu.", null);
			}
		}
		var file = cloudBackups.download(request.key());
		cloudBackups.verify(file, request.recoveryKey());
		return ApiResponse.ok("VERIFIED", "Bản sao lưu tải từ R2 đã giải mã và kiểm tra toàn vẹn");
	}

	@PostMapping("/cloud/prepare")
	public ApiResponse<StagedRestoreService.Prepared> prepareCloud(@RequestBody CloudVerifyRequest request) throws IOException {
		try {
			var file = cloudBackups.download(request.key());
			return ApiResponse.ok(stagedRestore.prepare(file, request.recoveryKey(),
					request.databaseUsername(), request.databasePassword()),
					"Đã dựng bản sao trên database riêng; dữ liệu đang làm việc không thay đổi");
		} catch (IOException e) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, e.getMessage(), null);
		}
	}

	public record CloudVerifyRequest(String key, String recoveryKey,
			String databaseUsername, String databasePassword) {}

	@GetMapping("/cloud")
	public ApiResponse<R2CloudService.Settings> cloudSettings() {
		return ApiResponse.ok(cloud.settings(), null);
	}

	@PutMapping("/cloud")
	public ApiResponse<R2CloudService.Settings> saveCloudSettings(@RequestBody R2CloudService.Input input) {
		try { return ApiResponse.ok(cloud.save(input), "Đã lưu cấu hình R2"); }
		catch (IllegalArgumentException e) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, e.getMessage(), null);
		}
	}

	@PostMapping("/cloud/test")
	public ApiResponse<String> testCloud() {
		try {
			cloud.testConnection();
			return ApiResponse.ok("CONNECTED", "Kết nối R2 thành công");
		} catch (Exception e) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Không kết nối được R2. Kiểm tra endpoint, bucket và khóa truy cập.", null);
		}
	}

	@GetMapping
	public ApiResponse<List<BackupLogDto>> list() {
		return ApiResponse.ok(service.listBackups(), null);
	}

	@PostMapping("/{id}/verify")
	public ApiResponse<String> verifyLocal(@PathVariable long id) {
		service.verifyLocal(id);
		return ApiResponse.ok("VERIFIED", "Bản sao lưu đã giải mã và kiểm tra toàn vẹn");
	}

	@PostMapping("/{id}/prepare")
	public ApiResponse<StagedRestoreService.Prepared> prepareLocal(@PathVariable long id,
			@RequestBody RestoreDatabaseCredentials credentials) throws IOException {
		try {
			return ApiResponse.ok(stagedRestore.prepare(service.verifiedArchive(id), null,
					credentials.username(), credentials.password()),
					"Đã dựng bản sao trên database riêng; dữ liệu đang làm việc không thay đổi");
		} catch (IOException e) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, e.getMessage(), null);
		}
	}

	public record RestoreDatabaseCredentials(String username, String password) {}

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
		throw new BusinessException(ErrorCode.RESTORE_UNAVAILABLE,
				"Tạm khóa khôi phục trực tiếp: cần xác minh bản sao trên database riêng trước khi thay dữ liệu đang dùng", null);
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
