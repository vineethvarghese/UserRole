package com.userrole.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void handleResourceNotFound_returnsHttp404WithCorrectCode() {
        ResourceNotFoundException ex = new ResourceNotFoundException("User with id 1 does not exist");
        ResponseEntity<ApiResponse<Void>> response = handler.handleResourceNotFound(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo("error");
        assertThat(response.getBody().getCode()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(response.getBody().getMessage()).isEqualTo("User with id 1 does not exist");
    }

    @Test
    void handleConflict_returnsHttp409WithCorrectCode() {
        ConflictException ex = new ConflictException("Username already exists");
        ResponseEntity<ApiResponse<Void>> response = handler.handleConflict(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("CONFLICT");
    }

    @Test
    void handleForbidden_returnsHttp403WithCorrectCode() {
        ForbiddenException ex = new ForbiddenException("Access denied");
        ResponseEntity<ApiResponse<Void>> response = handler.handleForbidden(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("FORBIDDEN");
    }

    @Test
    void handleValidation_returnsHttp400WithCorrectCode() {
        ValidationException ex = new ValidationException("Name must not be blank");
        ResponseEntity<ApiResponse<Void>> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void handleRateLimit_returnsHttp429WithRetryAfterHeader() {
        RateLimitException ex = new RateLimitException(60L);
        ResponseEntity<ApiResponse<Void>> response = handler.handleRateLimit(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("60");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("RATE_LIMIT_EXCEEDED");
    }

    @Test
    void handleAccessDenied_returnsHttp403() {
        AccessDeniedException ex = new AccessDeniedException("Access denied");
        ResponseEntity<ApiResponse<Void>> response = handler.handleAccessDenied(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("FORBIDDEN");
    }

    @Test
    void handleMethodArgumentNotValid_returnsHttp400WithFieldMessages() throws Exception {
        // Create a MethodArgumentNotValidException by simulating a binding result with errors
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "name", "Name must not be blank"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<ApiResponse<Void>> response = handler.handleMethodArgumentNotValid(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().getMessage()).contains("Name must not be blank");
    }

    @Test
    void handleGeneral_returnsHttp500WithoutLeakingDetails() {
        Exception ex = new RuntimeException("Unexpected internal failure");
        ResponseEntity<ApiResponse<Void>> response = handler.handleGeneral(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("INTERNAL_SERVER_ERROR");
        // Must not leak the internal message
        assertThat(response.getBody().getMessage()).doesNotContain("Unexpected internal failure");
    }

    @Test
    void resourceNotFoundException_isUnchecked() {
        assertThat(ResourceNotFoundException.class).hasSuperclass(UserRoleException.class);
        assertThat(UserRoleException.class).hasSuperclass(RuntimeException.class);
    }

    @Test
    void apiResponse_successFactory_setsCorrectStatus() {
        ApiResponse<String> response = ApiResponse.success("hello");
        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getData()).isEqualTo("hello");
        assertThat(response.getCode()).isNull();
    }

    @Test
    void apiResponse_errorFactory_setsCorrectFields() {
        ApiResponse<Void> response = ApiResponse.error("TEST_CODE", "test message");
        assertThat(response.getStatus()).isEqualTo("error");
        assertThat(response.getCode()).isEqualTo("TEST_CODE");
        assertThat(response.getMessage()).isEqualTo("test message");
        assertThat(response.getTimestamp()).isNotNull();
    }

    @Test
    void pageResponse_holdsDataAndMetaCorrectly() {
        PageMeta meta = new PageMeta(0, 10, 100L);
        PageResponse<String> page = new PageResponse<>(java.util.List.of("a", "b"), meta);

        assertThat(page.getData()).containsExactly("a", "b");
        assertThat(page.getMeta().getPage()).isEqualTo(0);
        assertThat(page.getMeta().getSize()).isEqualTo(10);
        assertThat(page.getMeta().getTotal()).isEqualTo(100L);
    }
}
