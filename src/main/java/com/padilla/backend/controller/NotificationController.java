package com.padilla.backend.controller;

import com.padilla.backend.dto.PageResponse;
import com.padilla.backend.exception.BadRequestException;
import com.padilla.backend.security.CurrentUser;
import com.padilla.backend.service.notification.NotificationPreferences;
import com.padilla.backend.util.Paging;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PRO-061: centro de notificaciones in-app (cada usuario ve solo las suyas).
 * PRO-062: preferencias del usuario.
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER', 'ADMIN', 'OWNER', 'TENANT', 'PROVIDER')")
public class NotificationController {

    private final JdbcTemplate jdbc;
    private final NotificationPreferences preferences;

    @Value("${app.db.schema}")
    private String schema;

    private static final String COLS = "id, tipo, titulo, mensaje, referencia_tipo AS \"referenciaTipo\", "
            + "referencia_id AS \"referenciaId\", prioridad, leida, leida_at AS \"leidaAt\", created_at AS \"createdAt\"";

    @GetMapping
    @Transactional(readOnly = true)
    public PageResponse<Map<String, Object>> list(@RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(required = false) Integer size,
                                                  @RequestParam(defaultValue = "false") boolean unread) {
        int s = Paging.request(page, size, null, java.util.Set.of(), org.springframework.data.domain.Sort.unsorted()).getPageSize();
        UUID me = CurrentUser.id();
        String where = " WHERE user_id = ?" + (unread ? " AND NOT leida" : "");
        Long total = jdbc.queryForObject("SELECT count(*) FROM " + schema + ".notification_log" + where, Long.class, me);
        List<Map<String, Object>> content = jdbc.queryForList("SELECT " + COLS + " FROM " + schema + ".notification_log"
                + where + " ORDER BY created_at DESC LIMIT ? OFFSET ?", me, s, (long) page * s);
        long t = total == null ? 0 : total;
        return new PageResponse<>(content, t, (int) Math.ceil(t / (double) s), page, s);
    }

    @GetMapping("/unread-count")
    @Transactional(readOnly = true)
    public Map<String, Long> unreadCount() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM " + schema + ".notification_log WHERE user_id = ? AND NOT leida",
                Long.class, CurrentUser.id());
        return Map.of("unread", n == null ? 0 : n);
    }

    @PatchMapping("/{id}/read")
    @Transactional
    public Map<String, Integer> markRead(@PathVariable UUID id) {
        int n = jdbc.update("UPDATE " + schema + ".notification_log SET leida = TRUE, leida_at = now() "
                + "WHERE id = ? AND user_id = ? AND NOT leida", id, CurrentUser.id());
        return Map.of("updated", n);
    }

    @PatchMapping("/read-all")
    @Transactional
    public Map<String, Integer> markAllRead() {
        int n = jdbc.update("UPDATE " + schema + ".notification_log SET leida = TRUE, leida_at = now() "
                + "WHERE user_id = ? AND NOT leida", CurrentUser.id());
        return Map.of("updated", n);
    }

    @GetMapping("/preferences")
    public Map<String, Object> getPreferences() {
        return preferences.get(CurrentUser.id());
    }

    public record PreferencesRequest(Boolean emailEnabled, String resumen) {}

    @PutMapping("/preferences")
    public Map<String, Object> savePreferences(@RequestBody PreferencesRequest req) {
        String resumen = req.resumen() == null ? "OFF" : req.resumen().toUpperCase();
        if (!NotificationPreferences.RESUMENES.contains(resumen)) {
            throw new BadRequestException("Resumen invalido: " + req.resumen() + ". Valores: " + NotificationPreferences.RESUMENES);
        }
        return preferences.save(CurrentUser.id(), req.emailEnabled() == null || req.emailEnabled(), resumen);
    }
}
