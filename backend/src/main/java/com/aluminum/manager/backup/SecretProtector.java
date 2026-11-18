package com.aluminum.manager.backup;

interface SecretProtector {
	byte[] protect(byte[] plain);
	byte[] unprotect(byte[] encrypted);
}
