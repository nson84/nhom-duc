package com.aluminum.manager.backup;

import com.aluminum.manager.backup.BackupDtos.BackupLogDto;
import com.aluminum.manager.backup.BackupDtos.BackupSettingsDto;
import com.aluminum.manager.backup.BackupDtos.BackupStatusDto;
import com.aluminum.manager.backup.BackupDtos.RestoreRequest;
import com.aluminum.manager.common.BusinessException;
import com.aluminum.manager.common.ErrorCode;
import com.aluminum.manager.security.CurrentAdmin;
import com.aluminum.manager.system.AuditService;
import com.aluminum.manager.system.SettingsService;
import com.aluminum.manager.system.ShopSettings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BackupService {

	private static final Logger log = LoggerFactory.getLogger(BackupService.class);
	private static final String BACKUP_HEADER = "-- ALUMINUM_MANAGER_BACKUP_V1";
	private static final String BACKUP_FOOTER = "-- ALUMINUM_MANAGER_BACKUP_END";
	private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

	private static final List<String> DATA_TABLES = List.of(
			"app_settings",
			"product_categories",
			"products",
			"product_units",
			"stock_balances",
			"document_sequences",
			"stock_documents",
			"stock_document_items",
			"stock_movements",
			"employees",
			"employee_pay_rates",
			"holidays",
			"attendance_policies",
			"attendance_records",
			"overtime_records",
			"payroll_periods",
			"payroll_details",
			"financial_transactions",
			"sales_payments");

	private final JdbcTemplate jdbc;
	private final DataSource dataSource;
	private final SettingsService settingsService;
	private final AuditService audit;
	private final Clock clock;
	private volatile LocalDate lastScheduledRunDate;

	public BackupService(
			JdbcTemplate jdbc,
			DataSource dataSource,
			SettingsService settingsService,
			AuditService audit,
			Clock clock) {
		this.jdbc = jdbc;
		this.dataSource = dataSource;
		this.settingsService = settingsService;
		this.audit = audit;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<BackupLogDto> listBackups() {
		return jdbc.query("""
				SELECT * FROM backup_logs ORDER BY started_at DESC, id DESC LIMIT 200
				""", (rs, i) -> mapLog(rs));
	}

	@Transactional(readOnly = true)
	public BackupStatusDto status() {
		ShopSettings settings = settingsService.get();
		List<BackupLogDto> latestList = jdbc.query(
				"SELECT * FROM backup_logs ORDER BY started_at DESC, id DESC LIMIT 1",
				(rs, i) -> mapLog(rs));
		BackupLogDto latest = latestList.isEmpty() ? null : latestList.get(0);
		boolean failed = latest != null && "FAILED".equals(latest.status());
		String warning = failed
				? "Lần sao lưu gần nhất đã thất bại: " + (latest.errorMessage() == null ? "Không rõ lỗi" : latest.errorMessage())
				: null;
		return new BackupStatusDto(
				latest,
				failed,
				warning,
				settings.backupTime(),
				settings.backupRetentionDays(),
				resolveBackupDirectory(settings.backupDirectory()).toAbsolutePath().toString());
	}

	@Transactional(readOnly = true)
	public BackupSettingsDto getSettings() {
		ShopSettings s = settingsService.get();
		return new BackupSettingsDto(s.backupTime(), s.backupRetentionDays(), s.backupDirectory());
	}

	@Transactional
	public BackupSettingsDto updateSettings(BackupSettingsDto request) {
		ShopSettings current = settingsService.get();
		ShopSettings updated = settingsService.update(new ShopSettings(
				current.shopName(),
				current.shopAddress(),
				current.shopPhone(),
				request.backupTime().trim(),
				request.backupRetentionDays(),
				request.backupDirectory() == null ? "" : request.backupDirectory().trim()));
		return new BackupSettingsDto(
				updated.backupTime(), updated.backupRetentionDays(), updated.backupDirectory());
	}

	public BackupLogDto createManualBackup() {
		Long adminId = CurrentAdmin.get().id();
		BackupLogDto result = performBackup("manual", adminId);
		if ("FAILED".equals(result.status())) {
			throw new BusinessException(ErrorCode.INTERNAL_ERROR,
					"Sao lưu thất bại: " + result.errorMessage(), null);
		}
		return result;
	}

	@Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
	public void scheduledBackupTick() {
		try {
			ShopSettings settings = settingsService.get();
			LocalTime target = LocalTime.parse(settings.backupTime());
			LocalDate today = LocalDate.now(clock);
			LocalTime now = LocalTime.now(clock);
			if (now.isBefore(target) || today.equals(lastScheduledRunDate)) {
				return;
			}
			Integer doneToday = jdbc.queryForObject("""
					SELECT COUNT(*) FROM backup_logs
					WHERE status = 'SUCCESS' AND DATE(started_at) = ?
					""", Integer.class, java.sql.Date.valueOf(today));
			if (doneToday != null && doneToday > 0) {
				lastScheduledRunDate = today;
				return;
			}
			lastScheduledRunDate = today;
			performBackup("scheduled", null);
		} catch (Exception ex) {
			log.warn("Không chạy được lịch sao lưu tự động", ex);
		}
	}

	public BackupLogDto restoreBackup(long backupId, RestoreRequest request) {
		if (!"KHOI PHUC".equals(request.confirmText().trim().toUpperCase())) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR,
					"Cần nhập đúng chuỗi xác nhận: KHOI PHUC", null);
		}
		List<BackupLogDto> rows = jdbc.query(
				"SELECT * FROM backup_logs WHERE id = ?", (rs, i) -> mapLog(rs), backupId);
		if (rows.isEmpty()) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Không tìm thấy bản ghi sao lưu", null);
		}
		BackupLogDto target = rows.get(0);
		if (!"SUCCESS".equals(target.status()) || target.backupPath() == null) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR,
					"Chỉ phục hồi được từ bản sao lưu ở trạng thái thành công", null);
		}

		Path file = Paths.get(target.backupPath());
		if (!Files.isRegularFile(file)) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR,
					"Không tìm thấy file sao lưu trên ổ đĩa: " + target.backupPath(), null);
		}

		String content;
		byte[] bytes;
		try {
			bytes = Files.readAllBytes(file);
			content = new String(bytes, StandardCharsets.UTF_8);
		} catch (IOException ex) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Không đọc được file sao lưu", null);
		}

		if (bytes.length == 0
				|| !content.startsWith(BACKUP_HEADER)
				|| !content.trim().endsWith(BACKUP_FOOTER)) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR,
					"File sao lưu không đúng định dạng hoặc bị cắt cụt, từ chối phục hồi", null);
		}

		String actualSha = sha256(bytes);
		if (target.checksumSha256() != null && !target.checksumSha256().equalsIgnoreCase(actualSha)) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR,
					"File sao lưu bị hỏng hoặc đã bị chỉnh sửa (lệch SHA-256), từ chối phục hồi", null);
		}

		Long adminId = CurrentAdmin.get().id();
		BackupLogDto preBackup = performBackup("pre-restore", adminId);
		if (!"SUCCESS".equals(preBackup.status())) {
			throw new BusinessException(ErrorCode.INTERNAL_ERROR,
					"Không tạo được bản sao lưu an toàn trước khi phục hồi: " + preBackup.errorMessage(), null);
		}

		executeRestoreSql(content);
		audit.record(adminId, "RESTORE_BACKUP", "backup_logs", String.valueOf(backupId),
				"Phục hồi từ " + target.backupPath() + ". Lý do: " + request.reason().trim()
						+ ". Bản sao lưu trước phục hồi: #" + preBackup.id());
		return target;
	}

	private BackupLogDto performBackup(String prefix, Long adminId) {
		ShopSettings settings = settingsService.get();
		Path dir = resolveBackupDirectory(settings.backupDirectory());
		LocalDateTime started = LocalDateTime.now(clock);
		String filename = "aluminum-" + prefix + "-" + started.format(FILE_TS) + ".sql";
		Path file = dir.resolve(filename);

		long logId = insertLog(file.toAbsolutePath().toString(), started);
		try {
			Files.createDirectories(dir);
			String sqlDump = buildDatabaseDump(started);
			byte[] bytes = sqlDump.getBytes(StandardCharsets.UTF_8);
			Files.write(file, bytes);
			if (!Files.isRegularFile(file) || Files.size(file) == 0) {
				throw new IOException("File sao lưu rỗng sau khi ghi");
			}
			String checksum = sha256(bytes);
			LocalDateTime completed = LocalDateTime.now(clock);
			jdbc.update("""
					UPDATE backup_logs
					SET completed_at = ?, status = 'SUCCESS', file_size_bytes = ?, checksum_sha256 = ?
					WHERE id = ?
					""", Timestamp.valueOf(completed), (long) bytes.length, checksum, logId);
			if (adminId != null) {
				audit.record(adminId, "CREATE_BACKUP", "backup_logs", String.valueOf(logId),
						"Sao lưu thành công (" + prefix + "): " + file.getFileName());
			}
			cleanupOldBackups(settings.backupRetentionDays());
		} catch (Exception ex) {
			log.error("Backup failed", ex);
			LocalDateTime completed = LocalDateTime.now(clock);
			jdbc.update("""
					UPDATE backup_logs
					SET completed_at = ?, status = 'FAILED', error_message = ?
					WHERE id = ?
					""", Timestamp.valueOf(completed), ex.getMessage(), logId);
		}
		return jdbc.query("SELECT * FROM backup_logs WHERE id = ?", (rs, i) -> mapLog(rs), logId).get(0);
	}

	private String buildDatabaseDump(LocalDateTime started) throws Exception {
		StringBuilder sb = new StringBuilder(65536);
		sb.append(BACKUP_HEADER).append("\n");
		sb.append("-- Generated at: ").append(started).append("\n");
		sb.append("SET FOREIGN_KEY_CHECKS = 0;\n");

		try (Connection conn = dataSource.getConnection();
				Statement stmt = conn.createStatement()) {
			for (int i = DATA_TABLES.size() - 1; i >= 0; i--) {
				sb.append("DELETE FROM `").append(DATA_TABLES.get(i)).append("`;\n");
			}
			for (String table : DATA_TABLES) {
				if ("product_units".equals(table) || "stock_balances".equals(table)) {
					sb.append("DELETE FROM `").append(table).append("`;\n");
				}
				List<String> writableCols = new ArrayList<>();
				try (ResultSet colRs = stmt.executeQuery("SHOW COLUMNS FROM `" + table + "`")) {
					while (colRs.next()) {
						String extra = colRs.getString("Extra");
						if (extra == null || !extra.toUpperCase().contains("GENERATED")) {
							writableCols.add(colRs.getString("Field"));
						}
					}
				}
				String colList = String.join(", ", writableCols.stream().map(c -> "`" + c + "`").toList());
				try (ResultSet rs = stmt.executeQuery("SELECT " + colList + " FROM `" + table + "`")) {
					ResultSetMetaData meta = rs.getMetaData();
					int cols = meta.getColumnCount();
					while (rs.next()) {
						sb.append("INSERT INTO `").append(table).append("` (").append(colList).append(") VALUES (");
						for (int c = 1; c <= cols; c++) {
							if (c > 1) {
								sb.append(", ");
							}
							Object val = rs.getObject(c);
							sb.append(toSqlLiteral(val));
						}
						sb.append(");\n");
					}
				}
			}
		}

		sb.append("SET FOREIGN_KEY_CHECKS = 1;\n");
		sb.append(BACKUP_FOOTER).append("\n");
		return sb.toString();
	}

	private void executeRestoreSql(String content) {
		String[] lines = content.split("\\r?\\n");
		try (Connection conn = dataSource.getConnection()) {
			boolean prevAutoCommit = conn.getAutoCommit();
			conn.setAutoCommit(false);
			try (Statement stmt = conn.createStatement()) {
				for (String rawLine : lines) {
					String line = rawLine.trim();
					if (line.isEmpty() || line.startsWith("--")) {
						continue;
					}
					if (line.endsWith(";")) {
						line = line.substring(0, line.length() - 1);
					}
					stmt.execute(line);
				}
				conn.commit();
			} catch (Exception ex) {
				conn.rollback();
				throw new BusinessException(ErrorCode.INTERNAL_ERROR,
						"Lỗi khi phục hồi dữ liệu: " + ex.getMessage(), null);
			} finally {
				conn.setAutoCommit(prevAutoCommit);
			}
		} catch (BusinessException ex) {
			throw ex;
		} catch (Exception ex) {
			throw new BusinessException(ErrorCode.INTERNAL_ERROR,
					"Không kết nối được cơ sở dữ liệu để phục hồi: " + ex.getMessage(), null);
		}
	}

	private void cleanupOldBackups(int retentionDays) {
		if (retentionDays < 1) {
			return;
		}
		LocalDateTime cutoff = LocalDateTime.now(clock).minusDays(retentionDays);
		List<BackupLogDto> oldLogs = jdbc.query("""
				SELECT * FROM backup_logs WHERE started_at < ? AND status = 'SUCCESS'
				""", (rs, i) -> mapLog(rs), Timestamp.valueOf(cutoff));
		for (BackupLogDto old : oldLogs) {
			if (old.backupPath() != null) {
				try {
					Files.deleteIfExists(Paths.get(old.backupPath()));
				} catch (IOException ignored) {
				}
			}
		}
	}

	private Path resolveBackupDirectory(String configured) {
		if (configured != null && !configured.isBlank()) {
			return Paths.get(configured.trim());
		}
		String appData = System.getenv("APPDATA");
		if (appData != null && !appData.isBlank()) {
			return Paths.get(appData, "AluminumManager", "backups");
		}
		return Paths.get("backups");
	}

	private long insertLog(String path, LocalDateTime started) {
		GeneratedKeyHolder keys = new GeneratedKeyHolder();
		jdbc.update(connection -> {
			PreparedStatement ps = connection.prepareStatement("""
					INSERT INTO backup_logs (backup_path, started_at, status)
					VALUES (?, ?, 'RUNNING')
					""", Statement.RETURN_GENERATED_KEYS);
			ps.setString(1, path);
			ps.setTimestamp(2, Timestamp.valueOf(started));
			return ps;
		}, keys);
		return keys.getKey().longValue();
	}

	private static BackupLogDto mapLog(ResultSet rs) throws java.sql.SQLException {
		return new BackupLogDto(
				rs.getLong("id"),
				rs.getString("backup_path"),
				rs.getTimestamp("started_at").toLocalDateTime(),
				rs.getTimestamp("completed_at") == null ? null : rs.getTimestamp("completed_at").toLocalDateTime(),
				rs.getString("status"),
				(Long) rs.getObject("file_size_bytes"),
				rs.getString("checksum_sha256"),
				rs.getString("error_message"),
				rs.getTimestamp("created_at").toLocalDateTime());
	}

	private static String toSqlLiteral(Object val) {
		if (val == null) {
			return "NULL";
		}
		if (val instanceof Boolean b) {
			return b ? "1" : "0";
		}
		if (val instanceof Number n) {
			return n.toString();
		}
		if (val instanceof Timestamp ts) {
			return "'" + ts.toLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")) + "'";
		}
		if (val instanceof LocalDateTime ldt) {
			return "'" + ldt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")) + "'";
		}
		String str = val.toString()
				.replace("\\", "\\\\")
				.replace("'", "\\'")
				.replace("\r", "\\r")
				.replace("\n", "\\n");
		return "'" + str + "'";
	}

	private static String sha256(byte[] bytes) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(md.digest(bytes));
		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}
}
