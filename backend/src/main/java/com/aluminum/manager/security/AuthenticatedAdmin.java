package com.aluminum.manager.security;

public record AuthenticatedAdmin(Long id, String username, boolean mustChangePassword) {
}
