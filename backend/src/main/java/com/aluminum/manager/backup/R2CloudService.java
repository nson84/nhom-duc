package com.aluminum.manager.backup;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.Properties;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import java.time.Duration;

@Service
public class R2CloudService {
	public record Settings(String endpoint, String bucket, String accessKeyId, boolean configured) {}
	public record Input(String endpoint, String bucket, String accessKeyId, String secretAccessKey) {}

	private final SecretProtector protector;
	private final Path file;

	@org.springframework.beans.factory.annotation.Autowired
	R2CloudService(SecretProtector protector) {
		this(protector, Path.of(System.getenv().getOrDefault("APPDATA", System.getProperty("user.home")),
				"AluminumManager", "r2-config.properties"));
	}

	R2CloudService(SecretProtector protector, Path file) {
		this.protector = protector;
		this.file = file;
	}

	public synchronized Settings settings() {
		Properties p = read();
		return new Settings(p.getProperty("endpoint", ""), p.getProperty("bucket", ""),
				p.getProperty("accessKeyId", ""), !p.getProperty("secret", "").isBlank());
	}

	public synchronized Settings save(Input input) {
		validate(input);
		Properties old = read();
		String encrypted = old.getProperty("secret", "");
		if (input.secretAccessKey() != null && !input.secretAccessKey().isBlank()) {
			byte[] protectedBytes = protector.protect(input.secretAccessKey().getBytes(StandardCharsets.UTF_8));
			encrypted = Base64.getEncoder().encodeToString(protectedBytes);
		}
		if (encrypted.isBlank()) throw new IllegalArgumentException("Nhập Secret Access Key");
		Properties p = new Properties();
		p.setProperty("endpoint", input.endpoint().trim());
		p.setProperty("bucket", input.bucket().trim());
		p.setProperty("accessKeyId", input.accessKeyId().trim());
		p.setProperty("secret", encrypted);
		try {
			Files.createDirectories(file.getParent());
			Path temp = Files.createTempFile(file.getParent(), "r2-config-", ".tmp");
			try {
				setOwnerOnly(temp);
				try (var out = Files.newOutputStream(temp)) { p.store(out, "R2 desktop settings"); }
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
			} finally { Files.deleteIfExists(temp); }
			return settings();
		} catch (IOException e) {
			throw new IllegalStateException("Không lưu được cấu hình R2", e);
		}
	}

	public synchronized void testConnection() {
		withClient(client -> {
			client.listObjectsV2(ListObjectsV2Request.builder().bucket(settings().bucket()).maxKeys(1).build());
			return null;
		});
	}

	<T> T withClient(Function<S3Client, T> action) {
		Properties p = read();
		if (p.getProperty("secret", "").isBlank()) throw new IllegalStateException("Chưa cấu hình R2");
		byte[] encrypted = Base64.getDecoder().decode(p.getProperty("secret"));
		String secret = new String(protector.unprotect(encrypted), StandardCharsets.UTF_8);
		try (S3Client client = S3Client.builder()
				.endpointOverride(URI.create(p.getProperty("endpoint")))
				.overrideConfiguration(ClientOverrideConfiguration.builder()
						.apiCallTimeout(Duration.ofSeconds(30))
						.apiCallAttemptTimeout(Duration.ofSeconds(10)).build())
				.region(Region.of("auto"))
				.credentialsProvider(StaticCredentialsProvider.create(
					AwsBasicCredentials.create(p.getProperty("accessKeyId"), secret)))
				.serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
				.build()) {
			return action.apply(client);
		}
	}

	private Properties read() {
		Properties p = new Properties();
		if (Files.exists(file)) {
			try (var in = Files.newInputStream(file)) { p.load(in); }
			catch (IOException e) { throw new IllegalStateException("Không đọc được cấu hình R2", e); }
		}
		return p;
	}

	private static void validate(Input input) {
		if (input == null || input.endpoint() == null || input.bucket() == null || input.accessKeyId() == null)
			throw new IllegalArgumentException("Thiếu cấu hình R2");
		URI uri = URI.create(input.endpoint().trim());
		if (!"https".equals(uri.getScheme()) || uri.getHost() == null
				|| !uri.getHost().matches("[a-fA-F0-9]{32}\\.r2\\.cloudflarestorage\\.com")
				|| uri.getUserInfo() != null || uri.getPort() != -1 || !"".equals(uri.getPath()))
			throw new IllegalArgumentException("Endpoint phải là địa chỉ S3 API của Cloudflare R2");
		if (!input.bucket().matches("[a-z0-9][a-z0-9-]{1,61}[a-z0-9]"))
			throw new IllegalArgumentException("Tên bucket không hợp lệ");
		if (input.accessKeyId().isBlank()) throw new IllegalArgumentException("Nhập Access Key ID");
	}

	static void setOwnerOnly(Path path) throws IOException {
		var posix = Files.getFileAttributeView(path, java.nio.file.attribute.PosixFileAttributeView.class);
		if (posix != null) {
			posix.setPermissions(java.nio.file.attribute.PosixFilePermissions.fromString(
					Files.isDirectory(path) ? "rwx------" : "rw-------"));
			return;
		}
		var view = Files.getFileAttributeView(path, java.nio.file.attribute.AclFileAttributeView.class);
		if (view == null) throw new IOException("Không thể đặt quyền riêng tư cho tệp cấu hình");
		var owner = Files.getOwner(path);
		view.setAcl(java.util.List.of(java.nio.file.attribute.AclEntry.newBuilder()
				.setType(java.nio.file.attribute.AclEntryType.ALLOW)
				.setPrincipal(owner)
				.setPermissions(java.nio.file.attribute.AclEntryPermission.values()).build()));
	}
}
