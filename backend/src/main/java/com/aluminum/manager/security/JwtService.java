package com.aluminum.manager.security;

import com.aluminum.manager.common.BusinessException;
import com.aluminum.manager.common.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

	private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
	private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

	private final byte[] secret;
	private final long ttlSeconds;
	private final ConcurrentHashMap<String, Long> revokedUntilEpoch = new ConcurrentHashMap<>();

	public JwtService(
			@Value("${aluminum.jwt.secret}") String secret,
			@Value("${aluminum.jwt.ttl-seconds:28800}") long ttlSeconds) {
		if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
			throw new IllegalArgumentException("JWT secret phải dài ít nhất 32 byte");
		}
		this.secret = secret.getBytes(StandardCharsets.UTF_8);
		this.ttlSeconds = ttlSeconds;
	}

	public long ttlSeconds() {
		return ttlSeconds;
	}

	public String issue(long userId, String username, String role, String shopCode) {
		Instant now = Instant.now();
		long exp = now.plusSeconds(ttlSeconds).getEpochSecond();
		String header = URL_ENCODER.encodeToString(
				"{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
		String payloadJson = "{\"uid\":" + userId
				+ ",\"usr\":\"" + escape(username) + "\""
				+ ",\"role\":\"" + escape(role == null ? "ADMIN" : role) + "\""
				+ ",\"shop\":\"" + escape(shopCode == null ? "" : shopCode) + "\""
				+ ",\"jti\":\"" + UUID.randomUUID() + "\""
				+ ",\"iat\":" + now.getEpochSecond()
				+ ",\"exp\":" + exp + "}";
		String payload = URL_ENCODER.encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
		String signingInput = header + "." + payload;
		return signingInput + "." + sign(signingInput);
	}

	public JwtClaims parse(String token) {
		String[] parts = token == null ? new String[0] : token.split("\\.");
		if (parts.length != 3) {
			throw unauthorized("Phiên đăng nhập không hợp lệ");
		}
		Map<String, String> header = parseFlatObject(decode(parts[0]));
		if (!"HS256".equals(header.get("alg"))) {
			throw unauthorized("Phiên đăng nhập không hợp lệ");
		}
		String signingInput = parts[0] + "." + parts[1];
		if (!MessageDigest.isEqual(sign(signingInput).getBytes(StandardCharsets.US_ASCII), parts[2].getBytes(StandardCharsets.US_ASCII))) {
			throw unauthorized("Phiên đăng nhập không hợp lệ");
		}
		Map<String, String> payload = parseFlatObject(decode(parts[1]));
		long exp = Long.parseLong(payload.get("exp"));
		if (Instant.now().getEpochSecond() >= exp) {
			throw unauthorized("Phiên đăng nhập đã hết hạn");
		}
		String jti = payload.get("jti");
		Long revokedUntil = revokedUntilEpoch.get(jti);
		if (revokedUntil != null) {
			if (revokedUntil > Instant.now().getEpochSecond()) {
				throw unauthorized("Phiên đăng nhập đã kết thúc");
			}
			revokedUntilEpoch.remove(jti);
		}
		String shop = payload.get("shop");
		return new JwtClaims(
				Long.parseLong(payload.get("uid")),
				payload.get("usr"),
				payload.getOrDefault("role", "ADMIN"),
				shop == null || shop.isBlank() ? null : shop,
				jti,
				exp);
	}

	public void revoke(String token) {
		JwtClaims claims = parse(token);
		revokedUntilEpoch.put(claims.jti(), claims.expiresAtEpochSecond());
	}

	private String sign(String signingInput) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret, "HmacSHA256"));
			return URL_ENCODER.encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII)));
		} catch (Exception ex) {
			throw new IllegalStateException("Không ký được phiên đăng nhập", ex);
		}
	}

	private static String decode(String value) {
		return new String(URL_DECODER.decode(value), StandardCharsets.UTF_8);
	}

	private static String escape(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private static BusinessException unauthorized(String message) {
		return new BusinessException(ErrorCode.UNAUTHORIZED, message, null);
	}

	static Map<String, String> parseFlatObject(String json) {
		Map<String, String> values = new LinkedHashMap<>();
		int i = 0;
		int n = json.length();
		if (n < 2 || json.charAt(0) != '{') {
			throw unauthorized("Phiên đăng nhập không hợp lệ");
		}
		i = 1;
		while (i < n) {
			i = skip(json, i);
			char marker = json.charAt(i);
			if (marker == '}') {
				break;
			}
			if (marker == ',') {
				i++;
				continue;
			}
			if (marker != '"') {
				throw unauthorized("Phiên đăng nhập không hợp lệ");
			}
			int keyEnd = json.indexOf('"', i + 1);
			String key = json.substring(i + 1, keyEnd);
			i = skip(json, keyEnd + 1);
			if (json.charAt(i) != ':') {
				throw unauthorized("Phiên đăng nhập không hợp lệ");
			}
			i = skip(json, i + 1);
			String value;
			if (json.charAt(i) == '"') {
				int end = json.indexOf('"', i + 1);
				value = json.substring(i + 1, end);
				i = end + 1;
			} else {
				int start = i;
				while (i < n && json.charAt(i) != ',' && json.charAt(i) != '}') {
					i++;
				}
				value = json.substring(start, i).trim();
			}
			values.put(key, value);
		}
		return values;
	}

	private static int skip(String json, int index) {
		int i = index;
		while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
			i++;
		}
		return i;
	}

	public record JwtClaims(
			long userId,
			String username,
			String role,
			String shopCode,
			String jti,
			long expiresAtEpochSecond) {
	}
}
