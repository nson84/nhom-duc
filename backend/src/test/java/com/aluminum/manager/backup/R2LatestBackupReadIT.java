package com.aluminum.manager.backup;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;

/** Opt-in, read-only check of the newest real archive; never writes or deletes an R2 object. */
class R2LatestBackupReadIT {
    @TempDir Path directory;

    @Test void newestCloudBackupMatchesMetadataAndLocalRecoveryKey() throws Exception {
        String config = System.getenv("ALUMINUM_TEST_R2_CONFIG_FILE");
        String keyFile = System.getenv("ALUMINUM_TEST_RECOVERY_KEY_FILE");
        assumeTrue(config != null && keyFile != null && System.getProperty("os.name").toLowerCase().contains("windows"));
        var protector = new WindowsSecretProtector();
        var cloud = new R2CloudService(protector, Path.of(config));
        var keys = new RecoveryKeyService(protector, Path.of(keyFile));
        assertTrue(cloud.settings().configured());
        assertTrue(Files.isRegularFile(Path.of(keyFile)), "Recovery key must already exist; do not generate a new one");
        String bucket = cloud.settings().bucket();
        var newest = cloud.withClient(client -> {
            var pages = client.listObjectsV2Paginator(ListObjectsV2Request.builder().bucket(bucket).prefix("backups/").build());
            return pages.contents().stream()
                    .filter(object -> object.key().endsWith(".ndbak"))
                    .max((left, right) -> left.lastModified().compareTo(right.lastModified()))
                    .orElse(null);
        });
        assertNotNull(newest, "No encrypted backup exists in the configured R2 bucket");
        Path archive = directory.resolve("latest.ndbak");
        var head = cloud.withClient(client -> {
            var metadata = client.headObject(HeadObjectRequest.builder().bucket(bucket).key(newest.key()).build());
            client.getObject(GetObjectRequest.builder().bucket(bucket).key(newest.key()).build(),
                    ResponseTransformer.toFile(archive));
            return metadata;
        });
        assertEquals(head.contentLength(), Files.size(archive));
        String expected = head.metadata().get("sha256");
        assertNotNull(expected, "Backup must contain its SHA-256 metadata");
        var digest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(archive)) {
            input.transferTo(new java.security.DigestOutputStream(OutputStream.nullOutputStream(), digest));
        }
        assertEquals(expected.toLowerCase(), HexFormat.of().formatHex(digest.digest()));
        new EncryptedBackupCodec().decrypt(archive, keys.key(), OutputStream.nullOutputStream());
        System.out.println("Verified newest R2 backup: " + newest.key() + " uploaded " + newest.lastModified());
    }
}
