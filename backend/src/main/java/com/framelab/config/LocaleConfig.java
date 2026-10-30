package com.framelab.config;

import java.util.Locale;

import com.framelab.common.i18n.LocaleSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.LocaleResolver;

@Configuration
public class LocaleConfig {

    @Bean
    public LocaleResolver localeResolver() {
        return new LocaleResolver() {
            @Override
            public Locale resolveLocale(HttpServletRequest request) {
                return LocaleSupport.resolve(request.getHeader(HttpHeaders.ACCEPT_LANGUAGE));
            }

            @Override
            public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
                // The application uses Accept-Language only; no server-side locale cookie.
            }
        };
    }
}
