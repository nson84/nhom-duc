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
	void restoreCannotModifyLiveDatabaseBeforeStagingExists() {
		BackupService service = mock(BackupService.class);
		BackupController controller = new BackupController(service);
		BusinessException error = assertThrows(BusinessException.class,
				() -> controller.restore(1L, new RestoreRequest("KHOI PHUC", "test")));
		assertEquals(ErrorCode.RESTORE_UNAVAILABLE, error.getCode());
		verifyNoInteractions(service);
	}
}
