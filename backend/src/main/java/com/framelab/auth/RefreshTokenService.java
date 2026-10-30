package com.framelab.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import com.framelab.config.SecurityProperties;
import com.framelab.user.RefreshSession;
import com.framelab.user.UserMapper;
import org.springframework.stereotype.Service;

@Service
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    private final UserMapper userMapper;
    private final SecurityProperties properties;

    public RefreshTokenService(UserMapper userMapper, SecurityProperties properties) {
        this.userMapper = userMapper;
        this.properties = properties;
    }

    public String issue(long userId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        userMapper.insertRefreshToken(userId, hash(raw), properties.getRefreshTokenTtl().toSeconds());
        return raw;
    }

    public RefreshSession findActive(String rawToken) {
        return rawToken == null || rawToken.isBlank() ? null : userMapper.findActiveSession(hash(rawToken));
    }

    public void touch(String rawToken) {
        if (rawToken != null && !rawToken.isBlank()) {
            userMapper.touchRefreshToken(hash(rawToken));
        }
    }

    public void revoke(String rawToken) {
        if (rawToken != null && !rawToken.isBlank()) {
            userMapper.revokeRefreshToken(hash(rawToken));
        }
    }

    public static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HEX.formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
