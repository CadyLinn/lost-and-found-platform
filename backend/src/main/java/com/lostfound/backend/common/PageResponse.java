package com.lostfound.backend.common;

import java.io.Serializable;
import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * A stable JSON shape for paged results, instead of serializing Spring's Page directly.
 * Serializable (when T is) so search results can be cached in Redis.
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages)
        implements Serializable {

    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                // An ArrayList (not toList()'s internal type) so Java serialization works everywhere.
                new java.util.ArrayList<>(page.getContent().stream().map(mapper).toList()),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
