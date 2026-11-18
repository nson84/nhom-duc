package com.aluminum.manager.backup;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

class CloudBackupServiceTest {
	@TempDir Path directory;

	@Test void uploadsEncryptedArchiveAndManifestOnlyOnce() throws Exception {
		byte[] key = new byte[32]; java.util.Arrays.fill(key, (byte) 3);
		Path archive = directory.resolve("aluminum-manual-20261115-120000-001.ndbak");
		EncryptedBackupCodec codec = new EncryptedBackupCodec();
		codec.encrypt("-- ALUMINUM_MANAGER_BACKUP_V2\n-- ALUMINUM_MANAGER_BACKUP_END\n".getBytes(), key, archive);
		String checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archive)));
		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		when(jdbc.query(anyString(), any(RowMapper.class))).thenAnswer(inv -> {
			RowMapper<?> mapper = inv.getArgument(1);
			var rs = mock(java.sql.ResultSet.class);
			when(rs.getString(1)).thenReturn(archive.toString());
			when(rs.getString(2)).thenReturn(checksum);
			return List.of(mapper.mapRow(rs, 0));
		});
		S3Client s3 = mock(S3Client.class);
		Set<String> stored = new HashSet<>();
		when(s3.headObject(any(HeadObjectRequest.class))).thenAnswer(inv -> {
			HeadObjectRequest request = inv.getArgument(0);
			if (!stored.contains(request.key())) throw S3Exception.builder().statusCode(404).build();
			return HeadObjectResponse.builder().contentLength(Files.size(archive))
					.metadata(Map.of("sha256", checksum)).build();
		});
		when(s3.putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class)))
				.thenAnswer(inv -> { stored.add(((PutObjectRequest) inv.getArgument(0)).key()); return PutObjectResponse.builder().build(); });
		R2CloudService cloud = new R2CloudService(mock(SecretProtector.class), directory.resolve("unused")) {
			@Override public Settings settings() { return new Settings("https://example", "bucket", "id", true); }
			@Override <T> T withClient(Function<S3Client, T> action) { return action.apply(s3); }
		};
		RecoveryKeyService recovery = mock(RecoveryKeyService.class);
		when(recovery.key()).thenReturn(key);
		CloudBackupService service = new CloudBackupService(jdbc, cloud, codec, recovery);
		assertEquals(1, service.syncPending());
		assertEquals(2, stored.size());
		assertEquals(0, service.syncPending());
		verify(s3, times(2)).putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class));
	}
}
