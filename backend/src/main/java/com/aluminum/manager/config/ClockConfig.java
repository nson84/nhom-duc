package com.aluminum.manager.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

	public static final ZoneId SHOP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

	@Bean
	public Clock clock() {
		return Clock.system(SHOP_ZONE);
	}
}
