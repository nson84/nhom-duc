package com.aluminum.manager.backup;

import com.sun.jna.platform.win32.Crypt32Util;
import org.springframework.stereotype.Component;

@Component
class WindowsSecretProtector implements SecretProtector {
	private void requireWindows() {
		if (!System.getProperty("os.name", "").toLowerCase().contains("windows"))
			throw new IllegalStateException("Lưu khóa R2 chỉ hỗ trợ Windows trên bản desktop này");
	}

	@Override
	public byte[] protect(byte[] plain) {
		requireWindows();
		return Crypt32Util.cryptProtectData(plain);
	}

	@Override
	public byte[] unprotect(byte[] encrypted) {
		requireWindows();
		return Crypt32Util.cryptUnprotectData(encrypted);
	}
}
