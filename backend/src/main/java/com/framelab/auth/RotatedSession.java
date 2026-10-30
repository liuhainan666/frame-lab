package com.framelab.auth;

import com.framelab.user.RefreshSession;

public record RotatedSession(String refreshToken, RefreshSession session) {
}
