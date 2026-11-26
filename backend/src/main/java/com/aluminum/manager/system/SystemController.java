package com.aluminum.manager.system;

import com.aluminum.manager.common.ApiResponse;
import com.aluminum.manager.common.BusinessException;
import com.aluminum.manager.common.ErrorCode;
import com.aluminum.manager.backup.RecoveryKeyService;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
public class SystemController {

	private final JdbcTemplate jdbc;
	private final ApplicationAvailability availability;
	@org.springframework.beans.factory.annotation.Autowired(required = false)
	private org.springframework.boot.info.BuildProperties build;
	@org.springframework.beans.factory.annotation.Autowired(required = false)
	private RecoveryKeyService recoveryKey;

	public SystemController(JdbcTemplate jdbc, ApplicationAvailability availability) {
		this.jdbc = jdbc;
		this.availability = availability;
	}

	@GetMapping("/ready")
	public ApiResponse<ReadyStatus> ready() {
		if (availability.getReadinessState() != ReadinessState.ACCEPTING_TRAFFIC)
			throw new BusinessException(ErrorCode.APPLICATION_STARTING, "Ứng dụng đang hoàn tất khởi động", null);
		if (recoveryKey != null && System.getenv("ALUMINUM_INITIAL_RECOVERY_KEY") != null)
			recoveryKey.key();
		jdbc.queryForObject("SELECT 1", Integer.class);
		String databaseName = jdbc.queryForObject("SELECT DATABASE()", String.class);
		String version = jdbc.queryForObject("""
				SELECT version FROM flyway_schema_history
				WHERE success = 1
				ORDER BY installed_rank DESC
				LIMIT 1
				""", String.class);
		return ApiResponse.ok(build == null ? new ReadyStatus("UP", "UP", databaseName, version)
				: new ReadyStatus("UP", "UP", databaseName, version, "aluminum-manager", build.getVersion(),
						build.get("revision") == null ? "unversioned" : build.get("revision")), null);
	}
}
