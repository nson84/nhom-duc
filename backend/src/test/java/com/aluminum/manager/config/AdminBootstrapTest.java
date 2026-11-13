package com.aluminum.manager.config;

import com.aluminum.manager.auth.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class AdminBootstrapTest {
    @Test
    void newAccountsUseCurrentDefaultPassword() {
        var users = mock(AdminUserRepository.class);
        var encoder = mock(PasswordEncoder.class);
        when(users.findByUsername(anyString())).thenReturn(Optional.empty());
        when(encoder.encode("1234567")).thenReturn("new-hash");

        new AdminBootstrap(users, encoder).run(null);

        verify(encoder, times(3)).encode("1234567");
        verify(users, times(3)).save(argThat(user -> "new-hash".equals(user.getPasswordHash())));
    }

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
