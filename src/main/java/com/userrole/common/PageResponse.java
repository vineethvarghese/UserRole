package com.userrole.common;

import java.util.List;

/**
 * Combined paginated response holding both the data list and pagination metadata.
 * Services return this type; controllers wrap it in ApiResponse.
 */
public class PageResponse<T> {

    private final List<T> data;
    private final PageMeta meta;

    public PageResponse(List<T> data, PageMeta meta) {
        this.data = data;
        this.meta = meta;
    }

    /** Convenience factory from a Spring Data Page result. */
    public static <T> PageResponse<T> from(org.springframework.data.domain.Page<T> springPage, int requestedPage) {
        PageMeta meta = new PageMeta(requestedPage, springPage.getSize(), springPage.getTotalElements());
        return new PageResponse<>(springPage.getContent(), meta);
    }

    public List<T> getData() {
        return data;
    }

    public PageMeta getMeta() {
        return meta;
    }
}
