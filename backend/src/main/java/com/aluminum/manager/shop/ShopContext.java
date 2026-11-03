package com.aluminum.manager.shop;

/**
 * Cửa hàng của request hiện tại, lấy từ header {@code X-Shop-Code}.
 * Mặc định là xưởng sản xuất.
 */
public final class ShopContext {

	public static final String HEADER = "X-Shop-Code";
	public static final String DISTRIBUTION = "DISTRIBUTION";
	public static final String MANUFACTURING = "MANUFACTURING";

	private static final ThreadLocal<String> CODE = new ThreadLocal<>();

	private ShopContext() {
	}

	public static void set(String code) {
		CODE.set(normalize(code));
	}

	public static String code() {
		String value = CODE.get();
		return value == null ? MANUFACTURING : value;
	}

	public static void clear() {
		CODE.remove();
	}

	public static String normalize(String code) {
		if (DISTRIBUTION.equalsIgnoreCase(code)) {
			return DISTRIBUTION;
		}
		return MANUFACTURING;
	}
}
