package com.aluminum.manager.backup;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class StagedRestoreServiceTest {
	@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;
	@Test void refusesCatalogSwitchBeforeOpeningAnyDatabaseConnection() throws Exception {
		byte[] key = new byte[32]; java.util.Arrays.fill(key, (byte) 7);
		var codec = new EncryptedBackupCodec();
		var archive = directory.resolve("unsafe.ndbak");
		codec.encrypt("-- ALUMINUM_MANAGER_BACKUP_V2\nUSE aluminum_app;\n-- ALUMINUM_MANAGER_BACKUP_END\n".getBytes(java.nio.charset.StandardCharsets.UTF_8), key, archive);
		var recovery = org.mockito.Mockito.mock(RecoveryKeyService.class);
		org.mockito.Mockito.when(recovery.key()).thenReturn(key);
		var service = new StagedRestoreService(codec, recovery, "invalid-no-database-may-be-opened", "test", "test");
		var refused = assertThrows(java.io.IOException.class, () -> service.prepare(archive, null, "test", "test"));
		assertTrue(refused.getMessage().contains("định dạng sao lưu an toàn"));
	}

	@Test void stagingUrlCannotPointAtActiveDatabase() {
		String active = "jdbc:mysql://127.0.0.1:3306/aluminum_app?useUnicode=true&createDatabaseIfNotExist=true";
		String staged = StagedRestoreService.stagingUrl(active, "aluminum_restore_012345abcdef");
		assertTrue(staged.contains("/aluminum_restore_012345abcdef?"));
		assertFalse(staged.contains("/aluminum_app?"));
		assertThrows(IllegalArgumentException.class, () -> StagedRestoreService.stagingUrl(active, "aluminum_app"));
	}
}
