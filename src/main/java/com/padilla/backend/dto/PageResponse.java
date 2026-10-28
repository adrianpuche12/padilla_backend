package com.padilla.backend.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * PRO-040: respuesta paginada uniforme de todos los listados.
 * page empieza en 0.
 */
public record PageResponse<T>(List<T> content, long totalElements, int totalPages, int page, int size) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getTotalElements(), page.getTotalPages(), page.getNumber(), page.getSize());
    }

    public static <T> PageResponse<T> ofMapped(Page<?> page, List<T> content) {
        return new PageResponse<>(content, page.getTotalElements(), page.getTotalPages(), page.getNumber(), page.getSize());
    }
}
