package com.aluminum.manager.config;

import com.aluminum.manager.auth.AdminUser;
import com.aluminum.manager.auth.AdminUserRepository;
import com.aluminum.manager.auth.UserRole;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrap implements ApplicationRunner {
	private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
	private static final SecureRandom RANDOM = new SecureRandom();
	private final AdminUserRepository adminUsers;
	private final PasswordEncoder passwordEncoder;
	private final String devPassword;
	private final String credentialDirectory;

	public AdminBootstrap(AdminUserRepository adminUsers, PasswordEncoder passwordEncoder,
			@Value("${aluminum.bootstrap.dev-password:}") String devPassword,
			@Value("${aluminum.bootstrap.credential-directory:}") String credentialDirectory) {
		this.adminUsers = adminUsers;
		this.passwordEncoder = passwordEncoder;
		this.devPassword = devPassword;
		this.credentialDirectory = credentialDirectory;
	}

	@Override
	public void run(ApplicationArguments args) {
		List<Account> missing = new ArrayList<>();
		addIfMissing(missing, "admin", UserRole.ADMIN, null);
		addIfMissing(missing, "phanphoi", UserRole.DISTRIBUTION, "DISTRIBUTION");
		addIfMissing(missing, "xuong", UserRole.MANUFACTURING, "MANUFACTURING");
		if (missing.isEmpty()) return;
		List<String> passwords = missing.stream().map(account ->
				devPassword.isBlank() ? randomPassword() : devPassword).toList();
		if (devPassword.isBlank()) writeFirstRunCredentials(missing, passwords);
		for (int i = 0; i < missing.size(); i++) {
			Account account = missing.get(i);
			AdminUser user = new AdminUser(account.name(), passwordEncoder.encode(passwords.get(i)),
					account.role(), account.shopCode());
			user.setMustChangePassword(true);
			adminUsers.save(user);
		}
		log.info("Đã tạo {} tài khoản còn thiếu; tài khoản đã có được giữ nguyên", missing.size());
	}

	private void addIfMissing(List<Account> missing, String name, UserRole role, String shopCode) {
		if (adminUsers.findByUsername(name).isEmpty()) missing.add(new Account(name, role, shopCode));
	}

	private static String randomPassword() {
		byte[] bytes = new byte[24];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private void writeFirstRunCredentials(List<Account> accounts, List<String> passwords) {
		String appData = System.getenv("APPDATA");
		Path dir = !credentialDirectory.isBlank() ? Path.of(credentialDirectory)
				: appData != null && !appData.isBlank() ? Path.of(appData, "AluminumManager")
				: Path.of(System.getProperty("user.home"), ".aluminum-manager");
		Path file = dir.resolve("first-run-credentials-" + LocalDateTime.now().format(
				DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")) + ".txt");
		try {
			Files.createDirectories(dir);
			if (Files.getFileAttributeView(dir, AclFileAttributeView.class) != null) {
				Files.createFile(file);
				var view = Files.getFileAttributeView(file, AclFileAttributeView.class);
				var owner = Files.getOwner(file);
				view.setAcl(List.of(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(owner)
						.setPermissions(AclEntryPermission.values()).build()));
			} else {
				Files.createFile(file, PosixFilePermissions.asFileAttribute(
						PosixFilePermissions.fromString("rw-------")));
			}
			StringBuilder content = new StringBuilder("Mật khẩu lần đầu; đăng nhập rồi đổi ngay. Xóa tệp sau khi lưu an toàn.\n");
			for (int i = 0; i < accounts.size(); i++)
				content.append(accounts.get(i).name()).append(": ").append(passwords.get(i)).append('\n');
			Files.writeString(file, content, StandardCharsets.UTF_8);
			log.warn("Tài khoản mới: đọc mật khẩu lần đầu tại {} rồi xóa tệp sau khi đổi mật khẩu", file);
		} catch (IOException ex) {
			throw new IllegalStateException("Không tạo được tệp mật khẩu lần đầu với quyền chỉ chủ máy; chưa tạo tài khoản", ex);
		}
	}

	private record Account(String name, UserRole role, String shopCode) { }
}
