package com.framelab.common.i18n;

import java.io.IOException;
import java.util.Locale;

import com.framelab.common.web.ResponseHeaders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class AcceptLanguageFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Locale locale = LocaleSupport.resolve(request.getHeader(HttpHeaders.ACCEPT_LANGUAGE));
        LocaleContextHolder.setLocale(locale);
        String path = request.getRequestURI();
        if (path.startsWith("/api/") || path.equals("/actuator/health")) {
            response.setHeader(HttpHeaders.CONTENT_LANGUAGE, LocaleSupport.tag(locale));
            ResponseHeaders.addVary(response, HttpHeaders.ACCEPT_LANGUAGE);
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            LocaleContextHolder.resetLocaleContext();
        }
    }
}
