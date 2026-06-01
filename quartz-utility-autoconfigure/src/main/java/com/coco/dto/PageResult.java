package com.coco.dto;

import java.util.List;

/**
 * Simple page result for query APIs.
 */
public class PageResult<T> {
    private final List<T> records;
    private final long total;
    private final int page;
    private final int size;

    public PageResult(List<T> records, long total, int page, int size) {
        this.records = records;
        this.total = total;
        this.page = page;
        this.size = size;
    }

    public List<T> getRecords() {
        return records;
    }

    public long getTotal() {
        return total;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public long getTotalPages() {
        if (size <= 0) {
            return 0;
        }
        return (total + size - 1) / size;
    }
}
