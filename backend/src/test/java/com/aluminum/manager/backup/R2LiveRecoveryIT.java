package com.aluminum.manager.backup;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Explicit opt-in only: a tiny encrypted fixture under a new verification/UUID key, never a business backup. */
class R2LiveRecoveryIT {
    @TempDir Path directory;

    @Test void configuredR2RoundTripsEncryptedFixtureAndRestoresOnIsolatedMysql() throws Exception {
        String config = System.getenv("ALUMINUM_TEST_R2_CONFIG_FILE");
        String url = System.getenv("ALUMINUM_TEST_DB_URL");
        assumeTrue(config != null && System.getProperty("os.name").toLowerCase().contains("windows"));
        assumeTrue(url != null && url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/aluminum_backup_test(?:\\?.*)?"));
        Path configPath = Path.of(config);
        byte[] originalConfig = Files.readAllBytes(configPath);
        var cloud = new R2CloudService(new WindowsSecretProtector(), configPath);
        assertTrue(cloud.settings().configured());
        cloud.testConnection();
        String key = "verification/" + UUID.randomUUID() + "/recovery.ndbak";
        byte[] recoveryKey = new byte[32]; new java.security.SecureRandom().nextBytes(recoveryKey);
        String marker = "R2 thử phục hồi " + UUID.randomUUID();
        String sql = "-- ALUMINUM_MANAGER_BACKUP_V2\nSET FOREIGN_KEY_CHECKS = 0;\nDELETE FROM `app_settings`;\n"
                + "INSERT INTO `app_settings` (`setting_key`, `setting_value`) VALUES ('r2.live.check', '" + marker + "');\n"
                + "SET FOREIGN_KEY_CHECKS = 1;\n-- ALUMINUM_MANAGER_BACKUP_END\n";
        var codec = new EncryptedBackupCodec();
        Path archive = directory.resolve("fixture.ndbak");
        codec.encrypt(sql.getBytes(StandardCharsets.UTF_8), recoveryKey, archive);
        byte[] encrypted = Files.readAllBytes(archive);
        String bucket = cloud.settings().bucket();
        try {
            cloud.withClient(client -> {
                client.putObject(software.amazon.awssdk.services.s3.model.PutObjectRequest.builder().bucket(bucket).key(key).build(),
                        software.amazon.awssdk.core.sync.RequestBody.fromBytes(encrypted));
                var head = client.headObject(software.amazon.awssdk.services.s3.model.HeadObjectRequest.builder().bucket(bucket).key(key).build());
                assertEquals(encrypted.length, head.contentLength());
                byte[] downloaded = client.getObjectAsBytes(software.amazon.awssdk.services.s3.model.GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray();
                assertArrayEquals(encrypted, downloaded);
                return null;
            });
            Path downloaded = directory.resolve("downloaded.ndbak");
            cloud.withClient(client -> {
                byte[] bytes = client.getObjectAsBytes(software.amazon.awssdk.services.s3.model.GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray();
                try { Files.write(downloaded, bytes); } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
                return null;
            });
            var keys = mock(RecoveryKeyService.class); when(keys.key()).thenReturn(recoveryKey);
            String username = System.getenv("ALUMINUM_TEST_DB_USERNAME");
            String password = System.getenv("ALUMINUM_TEST_DB_PASSWORD");
            var prepared = new StagedRestoreService(codec, keys, url, username, password).prepare(downloaded, null, username, password);
            var restored = new org.springframework.jdbc.core.JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(
                    StagedRestoreService.stagingUrl(url, prepared.databaseName()), username, password));
            assertEquals(marker, restored.queryForObject("SELECT setting_value FROM app_settings WHERE setting_key='r2.live.check'", String.class));
        } finally {
            // Delete exactly our newly generated test object, never a prefix or an existing backup.
            if (!key.matches("verification/[a-f0-9-]{36}/recovery\\.ndbak")) throw new IllegalStateException("Unexpected test object key");
            cloud.withClient(client -> { client.deleteObject(software.amazon.awssdk.services.s3.model.DeleteObjectRequest.builder().bucket(bucket).key(key).build()); return null; });
            assertArrayEquals(originalConfig, Files.readAllBytes(configPath), "Do not rewrite saved R2 configuration");
        }
    }
}
