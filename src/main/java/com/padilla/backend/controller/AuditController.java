package com.padilla.backend.controller;

import com.padilla.backend.dto.PageResponse;
import com.padilla.backend.util.Paging;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** PRO-073: log de auditoria (solo SUPER_ADMIN y MANAGER, ARQ 7.3). */
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER')")
public class AuditController {

    private final JdbcTemplate jdbc;

    @Value("${app.db.schema}")
    private String schema;

    @GetMapping("/api/audit")
    @Transactional(readOnly = true)
    public PageResponse<Map<String, Object>> list(@RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(required = false) Integer size,
                                                  @RequestParam(required = false) String entidad,
                                                  @RequestParam(required = false) UUID entidadId,
                                                  @RequestParam(required = false) UUID actorId,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        int s = Paging.request(page, size, null, Set.of(), Sort.unsorted()).getPageSize();
        StringBuilder where = new StringBuilder(" WHERE TRUE");
        List<Object> args = new ArrayList<>();
        if (entidad != null) { where.append(" AND a.entidad = ?"); args.add(entidad.toUpperCase()); }
        if (entidadId != null) { where.append(" AND a.entidad_id = ?"); args.add(entidadId); }
        if (actorId != null) { where.append(" AND a.actor_id = ?"); args.add(actorId); }
        if (from != null) { where.append(" AND a.created_at >= ?"); args.add(from); }
        if (to != null) { where.append(" AND a.created_at < ?"); args.add(to.plusDays(1)); }
        Long total = jdbc.queryForObject("SELECT count(*) FROM " + schema + ".audit_log a" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(s);
        pageArgs.add((long) page * s);
        List<Map<String, Object>> content = jdbc.queryForList("SELECT a.id, a.actor_id AS \"actorId\", u.name AS \"actorNombre\", "
                + "a.actor_rol AS \"actorRol\", a.accion, a.entidad, a.entidad_id AS \"entidadId\", a.detalle, a.created_at AS \"createdAt\" "
                + "FROM " + schema + ".audit_log a LEFT JOIN " + schema + ".users u ON u.id = a.actor_id" + where
                + " ORDER BY a.created_at DESC LIMIT ? OFFSET ?", pageArgs.toArray());
        long t = total == null ? 0 : total;
        return new PageResponse<>(content, t, (int) Math.ceil(t / (double) s), page, s);
    }
}
