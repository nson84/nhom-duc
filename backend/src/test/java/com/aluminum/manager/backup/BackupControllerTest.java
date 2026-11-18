package com.aluminum.manager.backup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.aluminum.manager.backup.BackupDtos.RestoreRequest;
import com.aluminum.manager.common.BusinessException;
import com.aluminum.manager.common.ErrorCode;
import org.junit.jupiter.api.Test;

class BackupControllerTest {
	@Test
	void invalidRecoveryKeyIsRejectedBeforeDownloadingFromR2() {
		CloudBackupService cloudBackups = mock(CloudBackupService.class);
		BackupController controller = new BackupController(mock(BackupService.class), mock(R2CloudService.class),
				cloudBackups, mock(RecoveryKeyService.class), mock(StagedRestoreService.class));

		BusinessException error = assertThrows(BusinessException.class,
				() -> controller.verifyCloud(new BackupController.CloudVerifyRequest("backups/example.ndbak",
						"1234567", null, null)));

		assertEquals(ErrorCode.VALIDATION_ERROR, error.getCode());
		verifyNoInteractions(cloudBackups);
	}

	@Test
	void restoreCannotModifyLiveDatabaseBeforeStagingExists() {
		BackupService service = mock(BackupService.class);
		BackupController controller = new BackupController(service, mock(R2CloudService.class),
				mock(CloudBackupService.class), mock(RecoveryKeyService.class), mock(StagedRestoreService.class));
		BusinessException error = assertThrows(BusinessException.class,
				() -> controller.restore(1L, new RestoreRequest("KHOI PHUC", "test")));
		assertEquals(ErrorCode.RESTORE_UNAVAILABLE, error.getCode());
		verifyNoInteractions(service);
	}
}
