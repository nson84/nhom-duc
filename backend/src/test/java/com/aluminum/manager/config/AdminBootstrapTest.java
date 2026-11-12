package com.aluminum.manager.config;

import com.aluminum.manager.auth.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class AdminBootstrapTest {
    @Test
    void restartPreservesPasswordsAndDisabledAccounts() {
        var users = mock(AdminUserRepository.class);
        var encoder = mock(PasswordEncoder.class);
        var existing = new AdminUser("admin", "changed-hash", UserRole.ADMIN, null);
        existing.setActive(false);
        when(users.findByUsername(anyString())).thenReturn(Optional.of(existing));
        new AdminBootstrap(users, encoder).run(null);
        assertEquals("changed-hash", existing.getPasswordHash());
        assertFalse(existing.isActive());
        verify(users, never()).save(any());
        verifyNoInteractions(encoder);
    }
}
