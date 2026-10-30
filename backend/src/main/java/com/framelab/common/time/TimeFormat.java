package com.framelab.common.time;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

public final class TimeFormat {

    private TimeFormat() {
    }

    public static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    public static LocalDateTime toUtcLocal(Instant value) {
        return value == null ? null : LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
