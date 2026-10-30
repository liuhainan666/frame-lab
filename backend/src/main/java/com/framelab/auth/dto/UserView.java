package com.framelab.auth.dto;

import java.time.Instant;

import com.framelab.common.time.TimeFormat;
import com.framelab.user.RefreshSession;
import com.framelab.user.User;

public record UserView(
        long id,
        String email,
        String displayName,
        String role,
        Instant createdAt) {

    public static UserView from(User user) {
        return new UserView(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole(),
                TimeFormat.toInstant(user.getCreatedAt()));
    }

    public static UserView from(RefreshSession session) {
        return new UserView(session.getUserId(), session.getEmail(), session.getDisplayName(), session.getRole(),
                TimeFormat.toInstant(session.getUserCreatedAt()));
    }
}
