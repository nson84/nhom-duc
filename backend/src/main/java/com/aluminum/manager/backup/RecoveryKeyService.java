package com.aluminum.manager.backup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Service;

@Service
public class RecoveryKeyService {
	private final SecretProtector protector;
	private final Path file;

	@org.springframework.beans.factory.annotation.Autowired
	RecoveryKeyService(SecretProtector protector) {
		this(protector, Path.of(System.getenv().getOrDefault("APPDATA", System.getProperty("user.home")),
				"AluminumManager", "backup-recovery-key.bin"));
	}

	RecoveryKeyService(SecretProtector protector, Path file) {
		this.protector = protector;
		this.file = file;
	}

	public synchronized byte[] key() {
		try {
			if (Files.exists(file)) {
				byte[] key = protector.unprotect(Files.readAllBytes(file));
				if (key.length != 32) throw new IllegalStateException("Khóa phục hồi bị hỏng");
				return key;
			}
			byte[] key = new byte[32];
			new SecureRandom().nextBytes(key);
			write(key);
			return key;
		} catch (IOException e) {
			throw new IllegalStateException("Không tạo được khóa phục hồi", e);
		}
	}

	public synchronized void importKey(String value) {
		byte[] supplied = parse(value);
		if (Files.exists(file)) {
			if (!java.security.MessageDigest.isEqual(key(), supplied))
				throw new IllegalStateException("Máy này đã có khóa phục hồi khác; không ghi đè khóa đang dùng");
			return;
		}
		try { write(supplied); }
		catch (IOException e) { throw new IllegalStateException("Không lưu được khóa phục hồi", e); }
	}

	private void write(byte[] value) throws IOException {
		Files.createDirectories(file.getParent());
		Path temp = Files.createTempFile(file.getParent(), "recovery-key-", ".tmp");
		try {
			R2CloudService.setOwnerOnly(temp);
			Files.write(temp, protector.protect(value));
			try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE); }
			catch (java.nio.file.AtomicMoveNotSupportedException e) { Files.move(temp, file); }
		} finally { Files.deleteIfExists(temp); }
	}

	public String exportKey() {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(key());
	}

	public static byte[] parse(String value) {
		byte[] decoded = Base64.getUrlDecoder().decode(value);
		if (decoded.length != 32) throw new IllegalArgumentException("Khóa phục hồi không hợp lệ");
		return decoded;
	}
}
