package com.framelab.auth;

import java.util.Locale;

import com.framelab.auth.dto.AuthResponse;
import com.framelab.auth.dto.LoginRequest;
import com.framelab.auth.dto.UserView;
import com.framelab.common.error.ApiException;
import com.framelab.common.error.ErrorCode;
import com.framelab.common.security.AuthUser;
import com.framelab.user.RefreshSession;
import com.framelab.user.User;
import com.framelab.user.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    // A valid cost-12 hash used to keep unknown-email logins timing-similar to wrong-password logins.
    private static final String DUMMY_HASH =
            "$2b$12$oDiRaSYrHZCgC1ttuA3YneJun5CYoQwnGS/PGB/falO5W9o/R3uQG";

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenService refreshTokenService;
    private final LoginRateLimiter loginRateLimiter;

    public AuthService(UserService userService,
                       PasswordEncoder passwordEncoder,
                       JwtTokenService jwtTokenService,
                       RefreshTokenService refreshTokenService,
                       LoginRateLimiter loginRateLimiter) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenService = refreshTokenService;
        this.loginRateLimiter = loginRateLimiter;
    }

    public AuthSession login(LoginRequest request, String ip) {
        String email = normalizeEmail(request.email());
        loginRateLimiter.checkAllowed(email, ip);

        User user = userService.findByEmail(email).orElse(null);
        String storedHash = user == null ? DUMMY_HASH : user.getPasswordHash();
        boolean passwordMatches = passwordEncoder.matches(request.password(), storedHash);
        if (user == null || !passwordMatches || !"ACTIVE".equals(user.getStatus())) {
            loginRateLimiter.recordFailure(email, ip);
            throw ApiException.of(ErrorCode.INVALID_CREDENTIALS);
        }

        loginRateLimiter.reset(email, ip);
        String refreshToken = refreshTokenService.issue(user.getId());
        AuthResponse response = new AuthResponse(
                jwtTokenService.createAccessToken(new AuthUser(user.getId(), user.getEmail(), user.getRole())),
                "Bearer",
                jwtTokenService.accessTokenTtlSeconds(),
                UserView.from(user));
        return new AuthSession(response, refreshToken);
    }

    public AuthResponse refresh(String rawRefreshToken) {
        RefreshSession session = refreshTokenService.findActive(rawRefreshToken);
        if (session == null || !"ACTIVE".equals(session.getStatus())) {
            throw ApiException.of(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        refreshTokenService.touch(rawRefreshToken);
        AuthUser authUser = new AuthUser(session.getUserId(), session.getEmail(), session.getRole());
        return new AuthResponse(jwtTokenService.createAccessToken(authUser), "Bearer",
                jwtTokenService.accessTokenTtlSeconds(), UserView.from(session));
    }

    public void logout(String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken);
    }

    public UserView me(AuthUser authUser) {
        return UserView.from(userService.requireActive(authUser.id()));
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
