package com.aluminum.manager.backup;

import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class BackupScheduleTest {
    private final LocalDateTime now = LocalDateTime.of(2026, 11, 21, 9, 0);

    @Test void firstTickBacksUpBeforeFourHoursHavePassed() {
        assertTrue(BackupService.backupDue(now.minusHours(1), now, 4, true));
        assertFalse(BackupService.backupDue(now.minusHours(1), now, 4, false));
    }

    @Test void quickRestartDoesNotCreateDuplicateBackup() {
        assertFalse(BackupService.backupDue(now.minusMinutes(2), now, 4, true));
        assertTrue(BackupService.backupDue(null, now, 4, true));
        assertTrue(BackupService.backupDue(now.minusHours(4), now, 4, false));
    }
}
