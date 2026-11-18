package com.aluminum.manager.backup;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class EncryptedBackupCodec {
	private static final byte[] MAGIC = { 'N', 'D', 'B', 'A', 'K', 1 };
	private static final int IV_SIZE = 12;

	public void encrypt(byte[] plain, byte[] key, Path target) throws IOException {
		byte[] iv = new byte[IV_SIZE];
		new SecureRandom().nextBytes(iv);
		try (OutputStream file = Files.newOutputStream(target)) {
			file.write(MAGIC);
			file.write(iv);
			Cipher cipher = init(Cipher.ENCRYPT_MODE, key, iv);
			cipher.updateAAD(MAGIC);
			try (GZIPOutputStream gzip = new GZIPOutputStream(new CipherOutputStream(file, cipher))) {
				gzip.write(plain);
			}
		} catch (GeneralSecurityException e) {
			throw new IOException("Không mã hóa được bản sao lưu", e);
		}
	}

	public void decrypt(Path source, byte[] key, OutputStream target) throws IOException {
		try (InputStream file = Files.newInputStream(source)) {
			byte[] magic = file.readNBytes(MAGIC.length);
			if (!Arrays.equals(magic, MAGIC)) throw new IOException("Định dạng backup không hợp lệ");
			byte[] iv = file.readNBytes(IV_SIZE);
			if (iv.length != IV_SIZE) throw new IOException("Bản backup bị cắt cụt");
			Cipher cipher = init(Cipher.DECRYPT_MODE, key, iv);
			cipher.updateAAD(MAGIC);
			try (GZIPInputStream gzip = new GZIPInputStream(new CipherInputStream(file, cipher))) {
				gzip.transferTo(target);
			}
		} catch (GeneralSecurityException e) {
			throw new IOException("Không giải mã được bản sao lưu", e);
		}
	}

	private static Cipher init(int mode, byte[] key, byte[] iv) throws GeneralSecurityException {
		if (key.length != 32) throw new GeneralSecurityException("Khóa phải dài 256 bit");
		Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
		cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
		return cipher;
	}
}
