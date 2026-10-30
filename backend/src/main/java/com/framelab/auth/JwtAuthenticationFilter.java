package com.framelab.auth;

import java.io.IOException;
import java.util.List;

import com.framelab.common.error.ErrorCode;
import com.framelab.common.security.AuthUser;
import com.framelab.common.web.RequestAttributes;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenService jwtTokenService;

    public JwtAuthenticationFilter(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            if (token.isEmpty()) {
                request.setAttribute(RequestAttributes.AUTH_ERROR, ErrorCode.TOKEN_INVALID);
            } else {
                authenticate(request, token);
            }
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request, String token) {
        try {
            AuthUser authUser = jwtTokenService.parse(token);
            var authentication = new UsernamePasswordAuthenticationToken(authUser, null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + authUser.role())));
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (TokenExpiredException ex) {
            request.setAttribute(RequestAttributes.AUTH_ERROR, ErrorCode.TOKEN_EXPIRED);
        } catch (RuntimeException ex) {
            request.setAttribute(RequestAttributes.AUTH_ERROR, ErrorCode.TOKEN_INVALID);
        }
    }
}
