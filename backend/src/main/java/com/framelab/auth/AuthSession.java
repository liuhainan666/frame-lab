package com.framelab.auth;

import com.framelab.auth.dto.AuthResponse;

public record AuthSession(AuthResponse response, String refreshToken) {
}
