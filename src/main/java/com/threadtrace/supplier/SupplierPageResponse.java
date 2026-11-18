package com.threadtrace.supplier;

import java.util.List;

public record SupplierPageResponse<T>(
        List<T> content,
        PageMetadata page
) {
    public record PageMetadata(
            int size,
            int number,
            long totalElements,
            int totalPages
    ) {}
}
