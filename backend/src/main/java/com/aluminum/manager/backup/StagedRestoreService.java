package com.aluminum.manager.backup;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Builds a separate MySQL database. Never opens the application's active database for writing. */
@Service
public class StagedRestoreService {
	public record Prepared(String databaseName, int sqlStatements, int imagesVerified, String imageDirectory) {}
	private final EncryptedBackupCodec codec;
	private final RecoveryKeyService recoveryKey;
	private final String activeUrl;
	private final String username;
	private final String password;

	StagedRestoreService(EncryptedBackupCodec codec, RecoveryKeyService recoveryKey,
			@Value("${spring.datasource.url}") String activeUrl,
			@Value("${spring.datasource.username}") String username,
			@Value("${spring.datasource.password}") String password) {
		this.codec = codec;
		this.recoveryKey = recoveryKey;
		this.activeUrl = activeUrl;
		this.username = username;
		this.password = password;
	}

	public Prepared prepare(Path archive, String suppliedKey) throws IOException {
		return prepare(archive, suppliedKey, username, password);
	}

	public Prepared prepare(Path archive, String suppliedKey, String restoreUsername, String restorePassword) throws IOException {
		if (restoreUsername == null || restoreUsername.isBlank() || restorePassword == null || restorePassword.isBlank())
			throw new IOException("Cần tài khoản MySQL có quyền tạo database thử");
		byte[] key = suppliedKey == null || suppliedKey.isBlank()
				? recoveryKey.key() : RecoveryKeyService.parse(suppliedKey);
		ByteArrayOutputStream plain = new ByteArrayOutputStream();
		codec.decrypt(archive, key, plain);
		String sql = plain.toString(StandardCharsets.UTF_8);
		if (!sql.startsWith("-- ALUMINUM_MANAGER_BACKUP_V2")
				|| !sql.trim().endsWith("-- ALUMINUM_MANAGER_BACKUP_END"))
			throw new IOException("Bản sao lưu không đầy đủ");
		int images = verifyImages(sql);
		String database = "aluminum_restore_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
		Path imageDirectory = Path.of(System.getenv().getOrDefault("APPDATA", System.getProperty("user.home")),
				"AluminumManager", "restore-staging", database, "image");
		Files.createDirectories(imageDirectory);
		R2CloudService.setOwnerOnly(imageDirectory);
		writeImages(sql, imageDirectory);
		String stageUrl = stagingUrl(activeUrl, database);
		try {
			Flyway.configure().dataSource(stageUrl, restoreUsername, restorePassword)
					.locations("classpath:db/migration").load().migrate();
		} catch (RuntimeException e) {
			throw new IOException("Không tạo được database thử; kiểm tra quyền CREATE DATABASE của tài khoản MySQL", e);
		}
		int statements = 0;
		Map<String, Integer> expectedRows = new HashMap<>();
		try (var connection = DriverManager.getConnection(stageUrl, restoreUsername, restorePassword);
				Statement stmt = connection.createStatement()) {
			for (String raw : sql.split("\\r?\\n")) {
				String line = raw.trim();
				if (line.isEmpty() || line.startsWith("--")) continue;
				if (line.startsWith("INSERT INTO `")) {
					int close = line.indexOf('`', 13);
					if (close > 13) expectedRows.merge(line.substring(13, close), 1, Integer::sum);
				}
				if (line.endsWith(";")) line = line.substring(0, line.length() - 1);
				stmt.execute(line);
				statements++;
			}
			stmt.execute("SET FOREIGN_KEY_CHECKS = 1");
			if (statements < 2) throw new IOException("Không có dữ liệu để khôi phục");
			for (var expected : expectedRows.entrySet()) {
				if (!expected.getKey().matches("[a-z_]+")) throw new IOException("Tên bảng không hợp lệ");
				try (var rs = stmt.executeQuery("SELECT COUNT(*) FROM `" + expected.getKey() + "`")) {
					rs.next();
					if (rs.getInt(1) != expected.getValue())
						throw new IOException("Số dòng khôi phục không khớp: " + expected.getKey());
				}
			}
			verifyForeignKeys(connection);
		} catch (Exception e) {
			throw new IOException("Khôi phục thử trên database riêng thất bại: " + database, e);
		}
		return new Prepared(database, statements, images, imageDirectory.toString());
	}

	// Re-enabling FOREIGN_KEY_CHECKS does not validate rows imported while it was disabled.
	private static void verifyForeignKeys(java.sql.Connection connection) throws java.sql.SQLException, IOException {
		var constraints = new java.util.LinkedHashMap<String, java.util.List<String[]>>();
		try (var query = connection.prepareStatement("""
				SELECT TABLE_NAME, COLUMN_NAME, REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME, CONSTRAINT_NAME
				FROM information_schema.KEY_COLUMN_USAGE
				WHERE TABLE_SCHEMA = DATABASE() AND REFERENCED_TABLE_NAME IS NOT NULL
				ORDER BY TABLE_NAME, CONSTRAINT_NAME, ORDINAL_POSITION
				"""); var rows = query.executeQuery()) {
			while (rows.next()) {
				String[] column = {rows.getString(1), rows.getString(2), rows.getString(3), rows.getString(4)};
				constraints.computeIfAbsent(column[0] + "." + rows.getString(5), ignored -> new java.util.ArrayList<>()).add(column);
			}
		}
		for (var constraint : constraints.entrySet()) {
			var columns = constraint.getValue();
			String nonNull = columns.stream().map(c -> "c." + identifier(c[1]) + " IS NOT NULL").collect(java.util.stream.Collectors.joining(" AND "));
			String matches = columns.stream().map(c -> "p." + identifier(c[3]) + " = c." + identifier(c[1])).collect(java.util.stream.Collectors.joining(" AND "));
			String sql = "SELECT 1 FROM " + identifier(columns.get(0)[0]) + " c WHERE " + nonNull
					+ " AND NOT EXISTS (SELECT 1 FROM " + identifier(columns.get(0)[2]) + " p WHERE " + matches + ") LIMIT 1";
			try (var query = connection.createStatement(); var rows = query.executeQuery(sql)) {
				if (rows.next()) throw new IOException("Quan hệ dữ liệu khôi phục không hợp lệ: " + constraint.getKey());
			}
		}
	}

	private static String identifier(String value) {
		return "`" + value.replace("`", "``") + "`";
	}

	static String stagingUrl(String activeUrl, String database) {
		if (!database.matches("aluminum_restore_[a-f0-9]{12}"))
			throw new IllegalArgumentException("Tên database thử không hợp lệ");
		int slash = activeUrl.indexOf('/', "jdbc:mysql://".length());
		if (!activeUrl.startsWith("jdbc:mysql://") || slash < 0)
			throw new IllegalArgumentException("Chỉ hỗ trợ MySQL Local");
		int query = activeUrl.indexOf('?', slash);
		String options = query < 0 ? "?createDatabaseIfNotExist=true" : activeUrl.substring(query);
		if (options.contains("createDatabaseIfNotExist=false"))
			options = options.replace("createDatabaseIfNotExist=false", "createDatabaseIfNotExist=true");
		else if (!options.contains("createDatabaseIfNotExist=true")) options += "&createDatabaseIfNotExist=true";
		return activeUrl.substring(0, slash + 1) + database + options;
	}

	private static int verifyImages(String sql) throws IOException {
		int count = 0;
		for (String line : sql.split("\\r?\\n")) {
			if (!line.startsWith("-- IMAGE ")) continue;
			String[] parts = line.substring(9).split(" ", 2);
			if (parts.length != 2 || !parts[0].matches("[a-f0-9-]{36}\\.[a-zA-Z0-9.+-]+"))
				throw new IOException("Tên ảnh trong bản sao không hợp lệ");
			byte[] bytes = Base64.getDecoder().decode(parts[1]);
			if (!parts[0].startsWith(UUID.nameUUIDFromBytes(bytes).toString() + "."))
				throw new IOException("Ảnh trong bản sao không khớp nội dung");
			count++;
		}
		return count;
	}

	private static void writeImages(String sql, Path directory) throws IOException {
		for (String line : sql.split("\\r?\\n")) {
			if (!line.startsWith("-- IMAGE ")) continue;
			String[] parts = line.substring(9).split(" ", 2);
			Path image = directory.resolve(parts[0]);
			Files.write(image, Base64.getDecoder().decode(parts[1]));
			R2CloudService.setOwnerOnly(image);
		}
	}
}
