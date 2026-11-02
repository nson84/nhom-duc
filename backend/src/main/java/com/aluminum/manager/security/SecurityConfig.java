package com.aluminum.manager.security;

import com.aluminum.manager.auth.AdminUserRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(
			HttpSecurity http,
			JwtService jwtService,
			AdminUserRepository adminUsers) throws Exception {
		JwtAuthenticationFilter jwtAuthenticationFilter = new JwtAuthenticationFilter(jwtService, adminUsers);
		// Ung dung desktop gui Bearer token, khong dung cookie. CORS chi mo origin cua app.
		http.csrf(AbstractHttpConfigurer::disable)
				.cors(Customizer.withDefaults())
				.sessionManagement(session -> session.sessionCreationPolicy(
						org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/v1/system/ready").permitAll()
						// Landing page công khai: khách gửi form liên hệ. Chỉ đúng một endpoint này,
						// đọc danh sách khách thì vẫn phải đăng nhập.
						.requestMatchers(HttpMethod.POST, "/api/v1/public/leads").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/v1/public/shop-info").permitAll()
						.requestMatchers(HttpMethod.OPTIONS, "/api/**").permitAll()
						.anyRequest().authenticated())
				.exceptionHandling(handler -> handler.authenticationEntryPoint((request, response, ex) -> {
					response.setStatus(401);
					response.setCharacterEncoding("UTF-8");
					response.setContentType("application/json");
					response.getWriter().write(
							"{\"success\":false,\"code\":\"UNAUTHORIZED\",\"message\":\"Bạn cần đăng nhập\"}");
				}))
				.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public UserDetailsService userDetailsService() {
		return username -> {
			throw new UsernameNotFoundException(username);
		};
	}

	@Bean
	public CorsConfigurationSource corsConfigurationSource(
			@Value("${aluminum.cors.allowed-origins}") List<String> origins,
			@Value("${aluminum.cors.public-origins:}") List<String> publicOrigins) {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(origins);
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
		configuration.setAllowCredentials(false);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/**", configuration);

		// Landing page công khai chỉ được gọi 2 endpoint công khai, không được gọi API nội bộ.
		// Tách cấu hình riêng để mở rộng domain cho khách mà không nới quyền vào dữ liệu cửa hàng.
		if (publicOrigins != null && !publicOrigins.isEmpty()
				&& !(publicOrigins.size() == 1 && publicOrigins.get(0).isBlank())) {
			CorsConfiguration publicConfiguration = new CorsConfiguration();
			publicConfiguration.setAllowedOrigins(publicOrigins.stream().map(String::trim).toList());
			publicConfiguration.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
			publicConfiguration.setAllowedHeaders(List.of("Content-Type"));
			publicConfiguration.setAllowCredentials(false);
			source.registerCorsConfiguration("/api/v1/public/**", publicConfiguration);
		}
		return source;
	}
}
