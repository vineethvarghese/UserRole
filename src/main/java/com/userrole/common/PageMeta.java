package com.userrole.common;

/**
 * Pagination metadata included in all paginated responses.
 */
public class PageMeta {

    private final int page;
    private final int size;
    private final long total;

    public PageMeta(int page, int size, long total) {
        this.page = page;
        this.size = size;
        this.total = total;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public long getTotal() {
        return total;
    }
}
