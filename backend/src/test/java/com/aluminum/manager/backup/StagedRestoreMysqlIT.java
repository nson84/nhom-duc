package com.aluminum.manager.backup;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Run explicitly with ALUMINUM_TEST_DB_URL/USERNAME/PASSWORD. Creates a new database; never touches the named one. */
class StagedRestoreMysqlIT {
	@TempDir Path directory;

	@Test void rejectsRowsWithBrokenForeignKeysEvenWhenImportDisabledChecks() throws Exception {
		String url = System.getenv("ALUMINUM_TEST_DB_URL");
		assumeTrue(url != null && url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/aluminum_backup_test(?:\\?.*)?"));
		String username = System.getenv("ALUMINUM_TEST_DB_USERNAME");
		String password = System.getenv("ALUMINUM_TEST_DB_PASSWORD");
		assumeTrue(username != null && password != null);
		byte[] key = new byte[32]; java.util.Arrays.fill(key, (byte) 7);
		Path archive = directory.resolve("broken-fk.ndbak");
		String sql = "-- ALUMINUM_MANAGER_BACKUP_V2\nSET FOREIGN_KEY_CHECKS = 0;\n"
				+ "INSERT INTO `sales_payments` (`document_id`, `payment_date`, `amount`, `payment_method`) VALUES (999999, '2025-02-18', 1000, 'CASH');\n"
				+ "SET FOREIGN_KEY_CHECKS = 1;\n-- ALUMINUM_MANAGER_BACKUP_END\n";
		new EncryptedBackupCodec().encrypt(sql.getBytes(StandardCharsets.UTF_8), key, archive);
		RecoveryKeyService recovery = org.mockito.Mockito.mock(RecoveryKeyService.class);
		org.mockito.Mockito.when(recovery.key()).thenReturn(key);
		var service = new StagedRestoreService(new EncryptedBackupCodec(), recovery, url, username, password);
		assertThrows(java.io.IOException.class, () -> service.prepare(archive, null, username, password));
	}

	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
	void restoresSqlIntoFreshMysqlDatabase(boolean noBackslashEscapes) throws Exception {
		String url = System.getenv("ALUMINUM_TEST_DB_URL");
		assumeTrue(url != null && url.startsWith("jdbc:mysql://127.0.0.1:") && url.contains("/aluminum_backup_test"));
		String username = System.getenv("ALUMINUM_TEST_DB_USERNAME");
		String password = System.getenv("ALUMINUM_TEST_DB_PASSWORD");
		assumeTrue(username != null && password != null);
		if (noBackslashEscapes) url += (url.contains("?") ? "&" : "?") + "sessionVariables=sql_mode='NO_BACKSLASH_ESCAPES'";
		byte[] key = new byte[32]; java.util.Arrays.fill(key, (byte) 7);
		Path archive = directory.resolve("fixture.ndbak");
		byte[] image = "Test image bytes".getBytes(StandardCharsets.UTF_8);
		String imageName = java.util.UUID.nameUUIDFromBytes(image) + ".png";
		new EncryptedBackupCodec().encrypt(("-- ALUMINUM_MANAGER_BACKUP_V2\n"
				+ "SET FOREIGN_KEY_CHECKS = 0;\n"
				+ "DELETE FROM `app_settings`;\n"
				+ "INSERT INTO `app_settings` (`setting_key`, `setting_value`) VALUES ('shop.name', 'Nhôm O\\'Brien');\n"
				+ "SET FOREIGN_KEY_CHECKS = 1;\n"
				+ "-- IMAGE " + imageName + " " + java.util.Base64.getEncoder().encodeToString(image) + "\n"
				+ "-- ALUMINUM_MANAGER_BACKUP_END\n").getBytes(StandardCharsets.UTF_8), key, archive);
		RecoveryKeyService recovery = org.mockito.Mockito.mock(RecoveryKeyService.class);
		org.mockito.Mockito.when(recovery.key()).thenReturn(key);
		var service = new StagedRestoreService(new EncryptedBackupCodec(), recovery, url, username, password);
		var result = service.prepare(archive, null, username, password);
		assertTrue(result.databaseName().startsWith("aluminum_restore_"));
		assertTrue(result.sqlStatements() >= 3);
		assertEquals(1, result.imagesVerified());
		var restored = new org.springframework.jdbc.core.JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(
				StagedRestoreService.stagingUrl(url, result.databaseName()), username, password));
		assertEquals("Nhôm O'Brien", restored.queryForObject("SELECT setting_value FROM app_settings WHERE setting_key = 'shop.name'", String.class));
		assertTrue(Files.isDirectory(Path.of(result.imageDirectory())));
		assertArrayEquals(image, Files.readAllBytes(Path.of(result.imageDirectory()).resolve(imageName)));
	}
}
