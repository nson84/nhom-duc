package com.aluminum.manager.backup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aluminum.manager.backup.BackupDtos.BackupLogDto;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class DesktopBackupControlTest {
    @Test
    void devHandoffRejectsAnUnauthenticatedLocalRequest() {
        BackupService backups = mock(BackupService.class);
        DesktopBackupControl control = new DesktopBackupControl(backups);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> control.handoffCheck(null, request)).getStatusCode().value());
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> control.handoff(null, request, new MockHttpServletResponse())).getStatusCode().value());
        org.mockito.Mockito.verifyNoInteractions(backups);
    }

    @Test
    void requiresLoopbackAndValidTokenBeforeStartingBackup() {
        BackupService backups = mock(BackupService.class);
        DesktopBackupControl control = new DesktopBackupControl(backups);
        byte[] token = new byte[32];
        token[0] = 7;
        ReflectionTestUtils.setField(control, "token", token);
        String secret = HexFormat.of().formatHex(token);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.10");
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> control.backup("startup", secret, request)).getStatusCode().value());
        request.setRemoteAddr("127.0.0.1");
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> control.backup("startup", "wrong", request)).getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> control.backup("other", secret, request)).getStatusCode().value());
        BackupLogDto result = mock(BackupLogDto.class);
        when(result.status()).thenReturn("SUCCESS");
        when(backups.createDesktopBackup("shutdown")).thenReturn(result);
        control.backup("shutdown", secret, request);
        verify(backups).createDesktopBackup("shutdown");
    }
}
