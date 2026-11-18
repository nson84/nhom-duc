package com.aluminum.manager.backup;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

class BackupFrequencyServiceTest {
	@Test void defaultsToFourHoursAndAcceptsOnlyConfiguredChoices() {
		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		when(jdbc.query(anyString(), any(ResultSetExtractor.class))).thenReturn("4");
		BackupFrequencyService service = new BackupFrequencyService(jdbc);
		assertEquals(4, service.get());
		service.set(1); service.set(2); service.set(4); service.set(8); service.set(24);
		assertThrows(IllegalArgumentException.class, () -> service.set(3));
		verify(jdbc, times(5)).update(anyString(), anyString());
	}
}
