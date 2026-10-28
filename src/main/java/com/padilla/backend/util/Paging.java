package com.padilla.backend.util;

import com.padilla.backend.exception.BadRequestException;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.Locale;
import java.util.Set;

/**
 * PRO-040: helpers de paginacion y busqueda.
 * - size entre 1 y 200 (default 25).
 * - sort "campo,asc|desc" solo sobre una lista blanca (evita ordenar por columnas arbitrarias).
 */
public final class Paging {

    public static final int DEFAULT_SIZE = 25;
    public static final int MAX_SIZE = 200;

    private Paging() {
    }

    public static PageRequest request(int page, Integer size, String sort, Set<String> sortable, Sort defaultSort) {
        if (page < 0) {
            throw new BadRequestException("page tiene que ser 0 o mayor");
        }
        int s = size == null ? DEFAULT_SIZE : size;
        if (s < 1 || s > MAX_SIZE) {
            throw new BadRequestException("size tiene que estar entre 1 y " + MAX_SIZE);
        }
        return PageRequest.of(page, s, parseSort(sort, sortable, defaultSort));
    }

    static Sort parseSort(String sort, Set<String> sortable, Sort defaultSort) {
        if (sort == null || sort.isBlank()) {
            return defaultSort;
        }
        String[] parts = sort.split(",");
        String field = parts[0].trim();
        if (!sortable.contains(field)) {
            throw new BadRequestException("No se puede ordenar por '" + field + "'. Campos posibles: " + sortable);
        }
        boolean desc = parts.length > 1 && parts[1].trim().equalsIgnoreCase("desc");
        return Sort.by(desc ? Sort.Direction.DESC : Sort.Direction.ASC, field);
    }

    /** lower(expr) LIKE %q% (sin distinguir mayusculas). */
    public static Predicate contains(CriteriaBuilder cb, Expression<String> expr, String q) {
        return cb.like(cb.lower(expr), "%" + escape(q.toLowerCase(Locale.ROOT).trim()) + "%", '\\');
    }

    public static boolean hasText(String q) {
        return q != null && !q.isBlank();
    }

    private static String escape(String q) {
        return q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
