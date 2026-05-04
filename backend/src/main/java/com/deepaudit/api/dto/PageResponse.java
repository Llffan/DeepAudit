package com.deepaudit.api.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Stable list-response envelope. Spring's {@code PageImpl} JSON shape is
 * being phased out (Spring 3.3+ logs a warning), so we project to a small
 * record the frontend can rely on.
 */
public record PageResponse<T>(
    List<T> content,
    long total,
    int page,
    int size
) {
    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getTotalElements(),
            page.getNumber(), page.getSize());
    }
}
