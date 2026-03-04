package com.userrole.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Standard API response envelope for all endpoints.
 *
 * Success (single resource):  { "status": "success", "data": {...} }
 * Success (paginated list):   { "status": "success", "data": {...}, "meta": {...} }
 * Error:                      { "status": "error", "code": "...", "message": "...", "timestamp": "..." }
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final String status;
    private final T data;
    private final PageMeta meta;
    private final String code;
    private final String message;
    private final Instant timestamp;

    private ApiResponse(String status, T data, PageMeta meta, String code, String message, Instant timestamp) {
        this.status = status;
        this.data = data;
        this.meta = meta;
        this.code = code;
        this.message = message;
        this.timestamp = timestamp;
    }

    /** Factory for a successful response wrapping a single resource or a page response. */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("success", data, null, null, null, null);
    }

    /** Factory for a successful paginated response — extracts PageMeta from the PageResponse. */
    public static <T> ApiResponse<PageResponse<T>> success(PageResponse<T> page) {
        return new ApiResponse<>("success", page, page.getMeta(), null, null, null);
    }

    /** Factory for an error response. */
    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>("error", null, null, code, message, Instant.now());
    }

    public String getStatus() {
        return status;
    }

    public T getData() {
        return data;
    }

    public PageMeta getMeta() {
        return meta;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
