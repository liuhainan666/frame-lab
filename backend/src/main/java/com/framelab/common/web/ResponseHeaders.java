package com.framelab.common.web;

import java.util.Arrays;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;

public final class ResponseHeaders {

    private ResponseHeaders() {
    }

    public static void addVary(HttpServletResponse response, String value) {
        for (String existing : response.getHeaders(HttpHeaders.VARY)) {
            boolean present = Arrays.stream(existing.split(","))
                    .map(String::trim)
                    .anyMatch(value::equalsIgnoreCase);
            if (present) {
                return;
            }
        }
        response.addHeader(HttpHeaders.VARY, value);
    }
}
