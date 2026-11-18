package com.aluminum.manager.backup;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class R2CloudServiceTest {
	@TempDir Path directory;

	private final SecretProtector protector = new SecretProtector() {
		@Override public byte[] protect(byte[] plain) {
			return ("encrypted:" + new String(plain, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);
		}
		@Override public byte[] unprotect(byte[] encrypted) {
			return new String(encrypted, StandardCharsets.UTF_8).substring(10).getBytes(StandardCharsets.UTF_8);
		}
	};

	@Test void storesSecretWithoutReturningIt() throws Exception {
		Path file = directory.resolve("r2.properties");
		R2CloudService service = new R2CloudService(protector, file);
		var input = new R2CloudService.Input("https://0123456789abcdef0123456789abcdef.r2.cloudflarestorage.com",
				"nhom-duc-backup", "access-key", "private-secret");
		assertTrue(service.save(input).configured());
		assertFalse(service.settings().toString().contains("private-secret"));
		assertFalse(Files.readString(file).contains("private-secret"));
		assertTrue(service.save(new R2CloudService.Input(input.endpoint(), input.bucket(), input.accessKeyId(), "")).configured());
	}

	@Test void rejectsNonR2Endpoint() {
		R2CloudService service = new R2CloudService(protector, directory.resolve("r2.properties"));
		assertThrows(IllegalArgumentException.class, () -> service.save(new R2CloudService.Input(
				"https://attacker.example.com", "nhom-duc-backup", "key", "secret")));
	}
}
