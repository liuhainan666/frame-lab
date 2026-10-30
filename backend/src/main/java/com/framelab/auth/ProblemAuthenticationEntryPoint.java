package com.framelab.auth;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.framelab.common.error.ErrorCode;
import com.framelab.common.error.ProblemFactory;
import com.framelab.common.web.RequestAttributes;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

public class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ProblemFactory problemFactory;
    private final ObjectMapper objectMapper;

    public ProblemAuthenticationEntryPoint(ProblemFactory problemFactory, ObjectMapper objectMapper) {
        this.problemFactory = problemFactory;
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {
        if (response.isCommitted()) {
            return;
        }
        Object attribute = request.getAttribute(RequestAttributes.AUTH_ERROR);
        ErrorCode code = attribute instanceof ErrorCode errorCode ? errorCode : ErrorCode.AUTH_REQUIRED;
        ProblemDetail problem = problemFactory.create(code, new Object[0], java.util.List.of(), request);
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
