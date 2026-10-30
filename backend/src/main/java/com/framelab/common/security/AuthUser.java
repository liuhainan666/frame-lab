package com.framelab.common.security;

public record AuthUser(long id, String email, String role, int tokenVersion) {
}
