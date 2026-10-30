package com.framelab.auth;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.framelab.common.security.AuthUser;
import com.framelab.config.SecurityProperties;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {

    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLE = "role";

    private final SecurityProperties properties;
    private final Clock clock;
    private final MACSigner signer;
    private final MACVerifier verifier;

    public JwtTokenService(SecurityProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        String secret = properties.getJwtSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("FRAMELAB_JWT_SECRET must be at least 32 bytes");
        }
        SecretKey key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        try {
            this.signer = new MACSigner(key);
            this.verifier = new MACVerifier(key);
        } catch (JOSEException ex) {
            throw new IllegalStateException("Unable to initialise JWT crypto", ex);
        }
    }

    public String createAccessToken(AuthUser user) {
        Instant now = clock.instant();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(properties.getIssuer())
                .audience(properties.getAudience())
                .subject(Long.toString(user.id()))
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(properties.getAccessTokenTtl())))
                .jwtID(UUID.randomUUID().toString())
                .claim(CLAIM_EMAIL, user.email())
                .claim(CLAIM_ROLE, user.role())
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        try {
            jwt.sign(signer);
        } catch (JOSEException ex) {
            throw new IllegalStateException("Unable to sign access token", ex);
        }
        return jwt.serialize();
    }

    public AuthUser parse(String token) {
        SignedJWT jwt;
        try {
            jwt = SignedJWT.parse(token);
        } catch (java.text.ParseException ex) {
            throw new TokenInvalidException();
        }
        try {
            if (!jwt.verify(verifier)) {
                throw new TokenInvalidException();
            }
        } catch (JOSEException ex) {
            throw new TokenInvalidException();
        }
        JWTClaimsSet claims;
        try {
            claims = jwt.getJWTClaimsSet();
        } catch (java.text.ParseException ex) {
            throw new TokenInvalidException();
        }
        if (!properties.getIssuer().equals(claims.getIssuer())) {
            throw new TokenInvalidException();
        }
        List<String> audience = claims.getAudience();
        if (audience == null || !audience.contains(properties.getAudience())) {
            throw new TokenInvalidException();
        }
        Date expiration = claims.getExpirationTime();
        if (expiration == null) {
            throw new TokenInvalidException();
        }
        if (!expiration.toInstant().isAfter(clock.instant())) {
            throw new TokenExpiredException();
        }
        String subject = claims.getSubject();
        long userId;
        try {
            userId = Long.parseLong(subject);
        } catch (NumberFormatException ex) {
            throw new TokenInvalidException();
        }
        String email;
        String role;
        try {
            email = claims.getStringClaim(CLAIM_EMAIL);
            role = claims.getStringClaim(CLAIM_ROLE);
        } catch (java.text.ParseException ex) {
            throw new TokenInvalidException();
        }
        if (email == null || role == null || (!role.equals("USER") && !role.equals("ADMIN"))) {
            throw new TokenInvalidException();
        }
        return new AuthUser(userId, email, role);
    }

    public long accessTokenTtlSeconds() {
        return properties.getAccessTokenTtl().toSeconds();
    }
}
