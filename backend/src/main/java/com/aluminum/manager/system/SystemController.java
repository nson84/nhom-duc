package com.aluminum.manager.system;

import com.aluminum.manager.common.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
public class SystemController {

	private final JdbcTemplate jdbc;

	public SystemController(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@GetMapping("/ready")
	public ApiResponse<ReadyStatus> ready() {
		jdbc.queryForObject("SELECT 1", Integer.class);
		String version = jdbc.queryForObject("""
				SELECT version FROM flyway_schema_history
				WHERE success = 1
				ORDER BY installed_rank DESC
				LIMIT 1
				""", String.class);
		return ApiResponse.ok(new ReadyStatus("UP", "UP", version), null);
	}
}
