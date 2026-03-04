package com.userrole.common;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Writes ADR-0009 error envelope JSON directly to HttpServletResponse.
 *
 * Used by servlet filters and Spring Security entry points where
 * {@code @RestControllerAdvice} cannot intercept exceptions.
 */
public final class ServletErrorResponseWriter {

    private ServletErrorResponseWriter() {
    }

    public static void writeError(HttpServletResponse response, int status,
                                  String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String body = """
                {"status":"error","code":"%s","message":"%s","timestamp":"%s"}"""
                .formatted(code, message, Instant.now().toString());
        response.getWriter().write(body);
    }
}
