package com.aluminum.manager.backup;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RecoveryKeyServiceTest {
	@TempDir Path directory;
	@Test void recoveryKeySurvivesRestartAndCanBeExported() {
		SecretProtector protector = new SecretProtector() {
			@Override public byte[] protect(byte[] plain) { return plain.clone(); }
			@Override public byte[] unprotect(byte[] encrypted) { return encrypted.clone(); }
		};
		Path file = directory.resolve("key.bin");
		String first = new RecoveryKeyService(protector, file).exportKey();
		String second = new RecoveryKeyService(protector, file).exportKey();
		assertEquals(first, second);
		assertEquals(32, RecoveryKeyService.parse(first).length);
		assertThrows(IllegalStateException.class, () -> new RecoveryKeyService(protector, file)
				.importKey(java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32])));
		Path newMachine = directory.resolve("new-machine-key.bin");
		RecoveryKeyService imported = new RecoveryKeyService(protector, newMachine);
		imported.importKey(first);
		assertEquals(first, imported.exportKey());
	}
}
