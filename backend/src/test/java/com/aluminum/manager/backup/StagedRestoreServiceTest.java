package com.aluminum.manager.backup;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class StagedRestoreServiceTest {
	@Test void stagingUrlCannotPointAtActiveDatabase() {
		String active = "jdbc:mysql://127.0.0.1:3306/aluminum_app?useUnicode=true&createDatabaseIfNotExist=true";
		String staged = StagedRestoreService.stagingUrl(active, "aluminum_restore_012345abcdef");
		assertTrue(staged.contains("/aluminum_restore_012345abcdef?"));
		assertFalse(staged.contains("/aluminum_app?"));
		assertThrows(IllegalArgumentException.class, () -> StagedRestoreService.stagingUrl(active, "aluminum_app"));
	}
}
