package com.aluminum.manager.backup;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class LocalBackupRetentionTest {
    @TempDir Path directory;

    @Test
    void keepsFiftyNewestAndOnlyPrunesMatchingArchivesInConfiguredDirectory() throws Exception {
        var jdbc = mock(JdbcTemplate.class);
        var service = new BackupService(jdbc, null, null, null, null, null, null, null, null);
        var active = Files.createDirectory(directory.resolve("active"));
        var other = Files.createDirectory(directory.resolve("other"));
        var bytes = new byte[]{1, 2, 3};
        String checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        List<BackupDtos.BackupLogDto> backups = new ArrayList<>();
        for (int id = 52; id > 0; id--) {
            var file = Files.write(active.resolve("aluminum-manual-20261126-123456-%03d.ndbak".formatted(id)), bytes);
            backups.add(row(id, file, checksum));
        }
        var elsewhere = Files.write(other.resolve("aluminum-manual-20261126-123456-000.ndbak"), bytes);
        var replaced = Files.write(active.resolve("aluminum-manual-20261126-123455-000.ndbak"), new byte[]{9});
        backups.add(row(53, elsewhere, checksum));
        backups.add(row(54, replaced, checksum));
        when(jdbc.query(contains("CASE WHEN id"), org.mockito.ArgumentMatchers.<RowMapper<BackupDtos.BackupLogDto>>any(), eq(52L)))
                .thenReturn(backups);

        service.pruneLocalBackups(active, 52L);

        for (int index = 0; index < 52; index++) {
            assertEquals(index < 50, Files.exists(Path.of(backups.get(index).backupPath())));
        }
        assertTrue(Files.exists(elsewhere));
        assertTrue(Files.exists(replaced));
        verify(jdbc, times(2)).update(contains("status = 'PRUNED'"), anyLong());
    }

    private static BackupDtos.BackupLogDto row(long id, Path path, String checksum) {
        return new BackupDtos.BackupLogDto(id, path.toString(), null, null, "SUCCESS", null, checksum, null, null);
    }
}
