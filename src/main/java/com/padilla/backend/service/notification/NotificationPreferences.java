package com.padilla.backend.service.notification;

import com.padilla.backend.enums.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * PRO-062: preferencias de notificacion por usuario. In-app siempre; mail on/off salvo tipos
 * obligatorios; resumen OFF / DIARIO / SEMANAL. Sin fila = valores por defecto (mail activo, sin resumen).
 */
@Service
@RequiredArgsConstructor
public class NotificationPreferences {

    public static final Set<String> RESUMENES = Set.of("OFF", "DIARIO", "SEMANAL");

    private final JdbcTemplate jdbc;

    @Value("${app.db.schema}")
    private String schema;

    @Transactional(readOnly = true)
    public boolean wantsEmail(UUID userId, NotificationType tipo) {
        if (tipo.isMandatory()) return true;
        List<Boolean> v = jdbc.queryForList("SELECT email_enabled FROM " + schema
                + ".notification_preferences WHERE user_id = ?", Boolean.class, userId);
        return v.isEmpty() || Boolean.TRUE.equals(v.get(0));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(UUID userId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT email_enabled, resumen FROM " + schema
                + ".notification_preferences WHERE user_id = ?", userId);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("emailEnabled", rows.isEmpty() ? Boolean.TRUE : rows.get(0).get("email_enabled"));
        r.put("resumen", rows.isEmpty() ? "OFF" : rows.get(0).get("resumen"));
        return r;
    }

    @Transactional
    public Map<String, Object> save(UUID userId, boolean emailEnabled, String resumen) {
        jdbc.update("INSERT INTO " + schema + ".notification_preferences (user_id, email_enabled, resumen) VALUES (?, ?, ?) "
                + "ON CONFLICT (user_id) DO UPDATE SET email_enabled = EXCLUDED.email_enabled, resumen = EXCLUDED.resumen, "
                + "updated_at = now()", userId, emailEnabled, resumen);
        return get(userId);
    }

    /** Usuarios que pidieron resumen con esa frecuencia (en la agencia de la transaccion). */
    @Transactional(readOnly = true)
    public List<UUID> withDigest(String resumen) {
        return jdbc.queryForList("SELECT p.user_id FROM " + schema + ".notification_preferences p JOIN " + schema
                + ".users u ON u.id = p.user_id WHERE p.resumen = ? AND u.active", UUID.class, resumen);
    }
}
