package com.aluminum.manager.backup;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EncryptedBackupCodecTest {
	@TempDir Path directory;
	private final EncryptedBackupCodec codec = new EncryptedBackupCodec();

	@Test void roundTripUnicodeAndCompression() throws Exception {
		byte[] key = new byte[32]; new SecureRandom().nextBytes(key);
		byte[] plain = ("-- ALUMINUM_MANAGER_BACKUP_V2\nINSERT INTO customer VALUES ('Nhôm Đức, Hải');\n"
				.repeat(1000) + "-- ALUMINUM_MANAGER_BACKUP_END\n").getBytes(StandardCharsets.UTF_8);
		Path archive = directory.resolve("test.ndbak");
		codec.encrypt(plain, key, archive);
		assertFalse(new String(Files.readAllBytes(archive), StandardCharsets.UTF_8).contains("Nhôm Đức"));
		assertTrue(Files.size(archive) < plain.length);
		ByteArrayOutputStream restored = new ByteArrayOutputStream();
		codec.decrypt(archive, key, restored);
		assertArrayEquals(plain, restored.toByteArray());
	}

	@Test void rejectsWrongKeyAndModifiedArchive() throws Exception {
		byte[] key = new byte[32]; new SecureRandom().nextBytes(key);
		Path archive = directory.resolve("test.ndbak");
		codec.encrypt("important".getBytes(StandardCharsets.UTF_8), key, archive);
		byte[] wrong = new byte[32]; new SecureRandom().nextBytes(wrong);
		assertThrows(IOException.class, () -> codec.decrypt(archive, wrong, new ByteArrayOutputStream()));
		byte[] modified = Files.readAllBytes(archive);
		modified[modified.length - 1] ^= 1;
		Files.write(archive, modified);
		assertThrows(IOException.class, () -> codec.decrypt(archive, key, new ByteArrayOutputStream()));
	}
}
