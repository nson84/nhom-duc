package com.aluminum.manager.backup;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Service
public class CloudBackupService {
	public record CloudObject(String key, long size, Instant uploadedAt) {}
	public record CloudStatus(boolean configured, int pending, String latestKey, String lastError) {}
	private record LocalBackup(Path path, String checksum) {}

	private final JdbcTemplate jdbc;
	private final R2CloudService cloud;
	private final EncryptedBackupCodec codec;
	private final RecoveryKeyService recoveryKey;
	private volatile String lastError;

	CloudBackupService(JdbcTemplate jdbc, R2CloudService cloud, EncryptedBackupCodec codec, RecoveryKeyService recoveryKey) {
		this.jdbc = jdbc;
		this.cloud = cloud;
		this.codec = codec;
		this.recoveryKey = recoveryKey;
	}

	public CloudStatus status() {
		if (!cloud.settings().configured()) return new CloudStatus(false, localBackups().size(), null, null);
		try {
			List<CloudObject> objects = list();
			var keys = objects.stream().map(CloudObject::key).collect(java.util.stream.Collectors.toSet());
			long pending = localBackups().stream().filter(b -> !keys.contains("backups/" + b.path().getFileName())).count();
			return new CloudStatus(true, (int) pending,
					objects.isEmpty() ? null : objects.get(0).key(), lastError);
		} catch (Exception e) {
			return new CloudStatus(true, localBackups().size(), null, "Không truy cập được R2; sẽ thử lại khi có kết nối");
		}
	}

	public List<CloudObject> list() {
		if (!cloud.settings().configured()) return List.of();
		String bucket = cloud.settings().bucket();
		return cloud.withClient(client -> {
			List<CloudObject> result = new ArrayList<>();
			String token = null;
			do {
				var page = client.listObjectsV2(ListObjectsV2Request.builder().bucket(bucket)
						.prefix("backups/").continuationToken(token).build());
				for (var object : page.contents()) {
					if (object.key().endsWith(".ndbak"))
						result.add(new CloudObject(object.key(), object.size(), object.lastModified()));
				}
				token = page.nextContinuationToken();
			} while (token != null);
			result.sort((a, b) -> b.uploadedAt().compareTo(a.uploadedAt()));
			return result;
		});
	}

	public synchronized int syncPending() {
		if (!cloud.settings().configured()) return 0;
		String bucket = cloud.settings().bucket();
		int uploaded = 0;
		boolean failed = false;
		for (LocalBackup backup : localBackups()) {
			if (!Files.isRegularFile(backup.path())) continue;
			String key = "backups/" + backup.path().getFileName();
			try {
				if (!backup.checksum().equalsIgnoreCase(sha256(backup.path())))
					throw new IllegalStateException("Checksum bản local không khớp");
				codec.decrypt(backup.path(), recoveryKey.key(), OutputStream.nullOutputStream());
				Boolean changed = cloud.withClient(client -> {
					var head = headOrNull(client, bucket, key);
					if (head != null && backup.checksum().equalsIgnoreCase(head.metadata().get("sha256"))
							&& head.contentLength() == backup.path().toFile().length()) {
						putManifestIfMissing(client, bucket, key, backup);
						return Boolean.FALSE;
					}
					try {
						client.putObject(PutObjectRequest.builder().bucket(bucket).key(key)
								.metadata(Map.of("sha256", backup.checksum(), "format", "ndbak-v1"))
								.contentType("application/octet-stream").build(), RequestBody.fromFile(backup.path()));
						var verified = client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
						if (!backup.checksum().equalsIgnoreCase(verified.metadata().get("sha256"))
								|| verified.contentLength() != backup.path().toFile().length())
							throw new IllegalStateException("R2 trả về kích thước hoặc checksum không khớp");
						putManifestIfMissing(client, bucket, key, backup);
					} catch (Exception e) { throw new IllegalStateException("Không đồng bộ được bản sao lưu", e); }
					return Boolean.TRUE;
				});
				if (Boolean.TRUE.equals(changed)) uploaded++;
			} catch (Exception e) {
				failed = true;
			}
		}
		lastError = failed ? "Có bản sao chưa đồng bộ; thử lại khi kết nối R2 hoạt động" : null;
		return uploaded;
	}

	public Path download(String key) throws IOException {
		validateKey(key);
		String bucket = cloud.settings().bucket();
		Path directory = Path.of(System.getenv().getOrDefault("APPDATA", System.getProperty("user.home")),
				"AluminumManager", "cloud-downloads");
		Files.createDirectories(directory);
		Path target = directory.resolve(key.substring("backups/".length()));
		Path temp = Files.createTempFile(directory, "r2-download-", ".tmp");
		try {
			cloud.withClient(client -> {
				var head = client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
				try (var out = Files.newOutputStream(temp)) {
					client.getObject(software.amazon.awssdk.services.s3.model.GetObjectRequest.builder()
						.bucket(bucket).key(key).build(), software.amazon.awssdk.core.sync.ResponseTransformer.toOutputStream(out));
				} catch (IOException e) { throw new IllegalStateException(e); }
				try {
					if (Files.size(temp) != head.contentLength() || !sha256(temp).equalsIgnoreCase(head.metadata().get("sha256")))
						throw new IllegalStateException("Bản tải xuống không khớp checksum R2");
				} catch (IOException e) { throw new IllegalStateException(e); }
				return null;
			});
			Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
			return target;
		} finally { Files.deleteIfExists(temp); }
	}

	public void verify(Path archive, String suppliedKey) throws IOException {
		byte[] key = suppliedKey == null || suppliedKey.isBlank()
				? recoveryKey.key() : RecoveryKeyService.parse(suppliedKey);
		ByteArrayOutputStream plain = new ByteArrayOutputStream();
		codec.decrypt(archive, key, plain);
		String sql = plain.toString(java.nio.charset.StandardCharsets.UTF_8);
		if (!sql.startsWith("-- ALUMINUM_MANAGER_BACKUP_V2")
				|| !sql.trim().endsWith("-- ALUMINUM_MANAGER_BACKUP_END"))
			throw new IOException("Nội dung backup thiếu đầu hoặc cuối");
	}

	@Scheduled(fixedDelay = 300_000, initialDelay = 60_000)
	public void retryPending() {
		if (cloud.settings().configured()) syncPending();
	}

	@Async
	public void syncAfterBackup() {
		retryPending();
	}

	private List<LocalBackup> localBackups() {
		return jdbc.query("SELECT backup_path, checksum_sha256 FROM backup_logs WHERE status = 'SUCCESS' AND backup_path LIKE '%.ndbak' ORDER BY id DESC LIMIT 200",
				(rs, i) -> new LocalBackup(Path.of(rs.getString(1)), rs.getString(2)));
	}

	private static software.amazon.awssdk.services.s3.model.HeadObjectResponse headOrNull(
			software.amazon.awssdk.services.s3.S3Client client, String bucket, String key) {
		try { return client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build()); }
		catch (S3Exception e) { if (e.statusCode() == 404) return null; throw e; }
	}

	private static void putManifestIfMissing(software.amazon.awssdk.services.s3.S3Client client,
			String bucket, String key, LocalBackup backup) {
		String manifestKey = key + ".manifest.json";
		if (headOrNull(client, bucket, manifestKey) != null) return;
		String json = "{\"formatVersion\":1,\"encryption\":\"AES-256-GCM\",\"compression\":\"GZIP\","
				+ "\"sha256\":\"" + backup.checksum() + "\",\"sizeBytes\":" + backup.path().toFile().length() + "}";
		client.putObject(PutObjectRequest.builder().bucket(bucket).key(manifestKey)
				.contentType("application/json").build(), RequestBody.fromString(json));
		if (headOrNull(client, bucket, manifestKey) == null)
			throw new IllegalStateException("Chưa xác minh được manifest trên R2");
	}

	private static void validateKey(String key) {
		if (key == null || !key.matches("backups/aluminum-[a-z-]+-[0-9-]+\\.ndbak"))
			throw new IllegalArgumentException("Tên bản sao trên R2 không hợp lệ");
	}

	private static String sha256(Path file) throws IOException {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			try (var in = Files.newInputStream(file)) { in.transferTo(new java.security.DigestOutputStream(OutputStream.nullOutputStream(), digest)); }
			return HexFormat.of().formatHex(digest.digest());
		} catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
	}
}
