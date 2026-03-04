package com.userrole.common;

/**
 * Caller-supplied pagination parameters.
 * Services accept this type at their interface boundary — not Spring Data's Pageable —
 * so services remain free of Spring Data coupling at the interface level.
 */
public class PageRequest {

    private final int page;
    private final int size;

    public PageRequest(int page, int size) {
        this.page = page > 0 ? page : 0;
        this.size = size > 0 ? size : 20;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    /** Convert to Spring Data Pageable for use inside service implementations. */
    public org.springframework.data.domain.PageRequest toSpringPageRequest() {
        return org.springframework.data.domain.PageRequest.of(page, size);
    }
}
