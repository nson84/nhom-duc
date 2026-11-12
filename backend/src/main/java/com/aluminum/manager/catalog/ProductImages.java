package com.aluminum.manager.catalog;

import com.aluminum.manager.common.BusinessException;
import com.aluminum.manager.common.ErrorCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class ProductImages {
	private static final String PREFIX = "product-image:";
	private static final Pattern DATA = Pattern.compile("^data:image/([a-zA-Z0-9.+-]+);base64,");
	private static final Pattern FILE = Pattern.compile("[a-f0-9-]{36}\\.[a-zA-Z0-9.+-]+");
	private final Path directory;

	ProductImages(@Value("${ALUMINUM_IMAGE_DIR:image}") String directory) {
		this.directory = Path.of(directory).toAbsolutePath().normalize();
	}

	String save(String value) {
		if (value == null || value.isBlank()) return null;
		var header = DATA.matcher(value);
		if (!header.find()) return value;
		byte[] bytes;
		try {
			bytes = Base64.getDecoder().decode(value.substring(header.end()));
		} catch (IllegalArgumentException ex) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Dữ liệu ảnh không hợp lệ", null);
		}
		String fileName = UUID.nameUUIDFromBytes(bytes) + "." + header.group(1);
		try {
			Files.createDirectories(directory);
			Path target = directory.resolve(fileName);
			if (Files.isRegularFile(target)) return PREFIX + fileName;
			Path temp = Files.createTempFile(directory, "upload-", ".tmp");
			try {
				Files.write(temp, bytes);
				Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} finally {
				Files.deleteIfExists(temp);
			}
			return PREFIX + fileName;
		} catch (IOException ex) {
			throw new IllegalStateException("Không thể lưu ảnh sản phẩm", ex);
		}
	}

	String display(String value) {
		if (value == null || !value.startsWith(PREFIX)) return value;
		String fileName = value.substring(PREFIX.length());
		if (!FILE.matcher(fileName).matches()) return null;
		try {
			String subtype = fileName.substring(fileName.indexOf('.') + 1);
			return "data:image/" + subtype + ";base64,"
					+ Base64.getEncoder().encodeToString(Files.readAllBytes(directory.resolve(fileName)));
		} catch (IOException ex) {
			return null;
		}
	}
}
