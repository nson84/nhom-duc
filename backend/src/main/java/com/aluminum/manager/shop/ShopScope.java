package com.aluminum.manager.shop;

/** {@code scope=all} trên query: gộp hai cửa hàng và tách giao dịch nội bộ khỏi số ngoài. */
public final class ShopScope {

	private static final ThreadLocal<Boolean> ALL = new ThreadLocal<>();

	private ShopScope() {
	}

	public static void setAll(boolean all) {
		ALL.set(all);
	}

	public static boolean all() {
		return Boolean.TRUE.equals(ALL.get());
	}

	public static void clear() {
		ALL.remove();
	}
}
