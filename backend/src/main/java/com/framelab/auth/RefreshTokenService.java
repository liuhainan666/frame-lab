package com.framelab.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import com.framelab.config.SecurityProperties;
import com.framelab.user.NewRefreshToken;
import com.framelab.user.RefreshSession;
import com.framelab.user.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    private final UserMapper userMapper;
    private final SecurityProperties properties;
    private final Clock clock;

    public RefreshTokenService(UserMapper userMapper, SecurityProperties properties, Clock clock) {
        this.userMapper = userMapper;
        this.properties = properties;
        this.clock = clock;
    }

    public String issue(long userId) {
        String raw = generateRaw();
        NewRefreshToken token = new NewRefreshToken();
        token.setUserId(userId);
        token.setFamilyId(UUID.randomUUID().toString());
        token.setTokenHash(hash(raw));
        token.setFamilyExpiresAt(utcNow().plus(properties.getRefreshTokenTtl()));
        userMapper.insertRefreshToken(token);
        return raw;
    }

    @Transactional
    public RotationOutcome rotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return RotationOutcome.invalid();
        }
        RefreshSession current = userMapper.findSessionForUpdate(hash(rawToken));
        LocalDateTime now = utcNow();
        if (current == null) {
            return RotationOutcome.invalid();
        }
        if (!current.getFamilyExpiresAt().isAfter(now) || !current.getExpiresAt().isAfter(now)) {
            userMapper.revokeFamily(current.getFamilyId(), "EXPIRED");
            return RotationOutcome.invalid();
        }
        if (!"ACTIVE".equals(current.getStatus())) {
            userMapper.revokeFamily(current.getFamilyId(), "DISABLED");
            return RotationOutcome.invalid();
        }
        if (current.getRevokedAt() != null) {
            if (isWithinRotationGrace(current, now)) {
                Child child = insertChild(current);
                return RotationOutcome.success(new RotatedSession(child.rawToken(), current));
            }
            if ("ROTATED".equals(current.getRevokedReason())) {
                log.warn("auth.refresh_reuse_detected userId={} familyId={}",
                        current.getUserId(), current.getFamilyId());
                userMapper.revokeFamily(current.getFamilyId(), "REUSE");
            }
            return RotationOutcome.invalid();
        }

        Child child = insertChild(current);
        int rotated = userMapper.markRotated(current.getTokenId(), child.id());
        if (rotated != 1) {
            log.warn("auth.refresh_rotation_conflict userId={} familyId={}",
                    current.getUserId(), current.getFamilyId());
            userMapper.revokeFamily(current.getFamilyId(), "REUSE");
            return RotationOutcome.invalid();
        }
        return RotationOutcome.success(new RotatedSession(child.rawToken(), current));
    }

    public RefreshSession findActive(String rawToken) {
        return rawToken == null || rawToken.isBlank() ? null : userMapper.findActiveSession(hash(rawToken));
    }

    public void touch(String rawToken) {
        if (rawToken != null && !rawToken.isBlank()) {
            userMapper.touchRefreshToken(hash(rawToken));
        }
    }

    @Transactional
    public void revokeFamily(String rawToken, String reason) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        RefreshSession session = userMapper.findSessionForUpdate(hash(rawToken));
        if (session != null) {
            userMapper.revokeFamily(session.getFamilyId(), reason);
        }
    }

    public void revokeAllForUser(long userId, String reason) {
        userMapper.revokeAllForUser(userId, reason);
    }

    public boolean isRotationEnabled() {
        return properties.isRefreshRotationEnabled();
    }

    private boolean isWithinRotationGrace(RefreshSession session, LocalDateTime now) {
        return "ROTATED".equals(session.getRevokedReason())
                && session.getRevokedAt().isAfter(now.minus(properties.getRefreshRotationGrace()));
    }

    private Child insertChild(RefreshSession parent) {
        String raw = generateRaw();
        NewRefreshToken token = new NewRefreshToken();
        token.setUserId(parent.getUserId());
        token.setFamilyId(parent.getFamilyId());
        token.setTokenHash(hash(raw));
        token.setFamilyExpiresAt(parent.getFamilyExpiresAt());
        userMapper.insertRefreshToken(token);
        return new Child(raw, token.getId());
    }

    private LocalDateTime utcNow() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private static String generateRaw() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HEX.formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private record Child(String rawToken, long id) {
    }
}
