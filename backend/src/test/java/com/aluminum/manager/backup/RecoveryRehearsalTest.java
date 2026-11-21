package com.aluminum.manager.backup;

import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class RecoveryRehearsalTest {
    @Test void disabledRehearsalNeverRunsScheduledBackupOrCloudSync() {
        var jdbc = mock(JdbcTemplate.class);
        var source = mock(javax.sql.DataSource.class);
        var settings = mock(com.aluminum.manager.system.SettingsService.class);
        var cloud = mock(R2CloudService.class);
        var codec = mock(EncryptedBackupCodec.class);
        var keys = mock(RecoveryKeyService.class);
        var frequency = mock(BackupFrequencyService.class);
        var sync = new CloudBackupService(jdbc, cloud, codec, keys);
        var backup = new BackupService(jdbc, source, settings, mock(com.aluminum.manager.system.AuditService.class),
                java.time.Clock.systemUTC(), keys, codec, frequency, sync);
        ReflectionTestUtils.setField(backup, "scheduledEnabled", false);
        ReflectionTestUtils.setField(sync, "scheduledEnabled", false);
        backup.scheduledBackupTick();
        backup.createShutdownBackup();
        sync.retryPending();
        sync.syncAfterBackup();
        verifyNoInteractions(jdbc, source, settings, cloud, codec, keys, frequency);
    }
}
