package com.framelab.common.util;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;

public final class Uuids {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    private Uuids() {
    }

    public static String newId() {
        return UUID.randomUUID().toString();
    }

    public static String newTraceId() {
        byte[] bytes = new byte[8];
        RANDOM.nextBytes(bytes);
        return HEX.formatHex(bytes);
    }
}
