package com.framelab.auth;

public record RotationOutcome(RotatedSession rotatedSession, boolean valid) {

    public static RotationOutcome success(RotatedSession session) {
        return new RotationOutcome(session, true);
    }

    public static RotationOutcome invalid() {
        return new RotationOutcome(null, false);
    }
}
