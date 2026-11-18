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

	@Test void restoresSqlIntoFreshMysqlDatabase() throws Exception {
		String url = System.getenv("ALUMINUM_TEST_DB_URL");
		assumeTrue(url != null && url.startsWith("jdbc:mysql://127.0.0.1:") && url.contains("/aluminum_backup_test"));
		String username = System.getenv("ALUMINUM_TEST_DB_USERNAME");
		String password = System.getenv("ALUMINUM_TEST_DB_PASSWORD");
		assumeTrue(username != null && password != null);
		byte[] key = new byte[32]; java.util.Arrays.fill(key, (byte) 7);
		Path archive = directory.resolve("fixture.ndbak");
		new EncryptedBackupCodec().encrypt(("-- ALUMINUM_MANAGER_BACKUP_V2\n"
				+ "SET FOREIGN_KEY_CHECKS = 0;\n"
				+ "DELETE FROM `app_settings`;\n"
				+ "INSERT INTO `app_settings` (`setting_key`, `setting_value`) VALUES ('shop.name', 'Nhôm Đức');\n"
				+ "SET FOREIGN_KEY_CHECKS = 1;\n"
				+ "-- ALUMINUM_MANAGER_BACKUP_END\n").getBytes(StandardCharsets.UTF_8), key, archive);
		RecoveryKeyService recovery = org.mockito.Mockito.mock(RecoveryKeyService.class);
		org.mockito.Mockito.when(recovery.key()).thenReturn(key);
		var service = new StagedRestoreService(new EncryptedBackupCodec(), recovery, url, username, password);
		var result = service.prepare(archive, null, username, password);
		assertTrue(result.databaseName().startsWith("aluminum_restore_"));
		assertTrue(result.sqlStatements() >= 3);
		assertEquals(0, result.imagesVerified());
		assertTrue(Files.isDirectory(Path.of(result.imageDirectory())));
	}
}
