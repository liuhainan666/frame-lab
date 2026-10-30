package com.framelab.auth;

import java.time.Duration;

import com.framelab.auth.dto.AuthResponse;
import com.framelab.auth.dto.LoginRequest;
import com.framelab.auth.dto.UserView;
import com.framelab.common.error.ApiException;
import com.framelab.common.security.SecurityUtils;
import com.framelab.config.SecurityProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final SecurityProperties securityProperties;

    public AuthController(AuthService authService, SecurityProperties securityProperties) {
        this.authService = authService;
        this.securityProperties = securityProperties;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletRequest httpRequest,
                                              HttpServletResponse httpResponse) {
        AuthSession session = authService.login(request, httpRequest.getRemoteAddr());
        httpResponse.addHeader(HttpHeaders.SET_COOKIE, refreshCookie(session.refreshToken()).toString());
        return ResponseEntity.ok(session.response());
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest request, HttpServletResponse httpResponse) {
        try {
            AuthSession session = authService.refresh(readRefreshCookie(request));
            if (session.refreshToken() != null) {
                httpResponse.addHeader(HttpHeaders.SET_COOKIE, refreshCookie(session.refreshToken()).toString());
            }
            return ResponseEntity.ok(session.response());
        } catch (ApiException ex) {
            httpResponse.addHeader(HttpHeaders.SET_COOKIE, clearedCookie().toString());
            throw ex;
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        authService.logout(readRefreshCookie(request));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearedCookie().toString())
                .build();
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll() {
        authService.logoutAll(SecurityUtils.currentUser());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearedCookie().toString())
                .build();
    }

    @GetMapping("/me")
    public UserView me() {
        return authService.me(SecurityUtils.currentUser());
    }

    private ResponseCookie refreshCookie(String refreshToken) {
        return baseCookie(refreshToken).maxAge(securityProperties.getRefreshTokenTtl()).build();
    }

    private ResponseCookie clearedCookie() {
        return baseCookie("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(securityProperties.getCookieName(), value)
                .httpOnly(true)
                .secure(securityProperties.isCookieSecure())
                .sameSite("Lax")
                .path("/api/auth");
    }

    private String readRefreshCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (securityProperties.getCookieName().equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
