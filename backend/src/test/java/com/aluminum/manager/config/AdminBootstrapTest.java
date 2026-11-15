package com.aluminum.manager.config;

import com.aluminum.manager.auth.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class AdminBootstrapTest {
	@TempDir Path credentialDirectory;

	@Test
	void freshProductionInstallCreatesRandomPasswordsInOwnerOnlyFile() throws Exception {
		var users = mock(AdminUserRepository.class);
		var encoder = mock(PasswordEncoder.class);
		when(users.findByUsername(anyString())).thenReturn(Optional.empty());
		when(encoder.encode(anyString())).thenReturn("new-hash");
		new AdminBootstrap(users, encoder, "", credentialDirectory.toString()).run(null);
		var passwords = ArgumentCaptor.forClass(String.class);
		verify(encoder, times(3)).encode(passwords.capture());
		assertEquals(3, passwords.getAllValues().stream().distinct().count());
		assertTrue(passwords.getAllValues().stream().allMatch(password -> password.length() >= 24));
		try (var files = Files.list(credentialDirectory)) {
			String text = Files.readString(files.findFirst().orElseThrow());
			for (String password : passwords.getAllValues()) assertTrue(text.contains(password));
		}
	}
    @Test
    void newAccountsUseCurrentDefaultPassword() {
        var users = mock(AdminUserRepository.class);
        var encoder = mock(PasswordEncoder.class);
        when(users.findByUsername(anyString())).thenReturn(Optional.empty());
        when(encoder.encode("1234567")).thenReturn("new-hash");

        new AdminBootstrap(users, encoder, "1234567", "").run(null);

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
        new AdminBootstrap(users, encoder, "1234567", "").run(null);
        assertEquals("changed-hash", existing.getPasswordHash());
        assertFalse(existing.isActive());
        verify(users, never()).save(any());
        verifyNoInteractions(encoder);
    }
}
