package com.padilla.backend.service.dashboard;

import com.padilla.backend.enums.Role;
import com.padilla.backend.exception.BadRequestException;
import com.padilla.backend.security.AgencyContext;
import com.padilla.backend.security.CurrentUser;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * PRO-070: Widget Registry.
 * - syncWidgets() al arrancar: inserta los nuevos, actualiza titulo/roles de los existentes y marca
 *   is_system_active=false los que ya no estan en el codigo (no los borra).
 * - available(): widgets permitidos para el rol del usuario, con su preferencia (o el default del rol).
 * - data(): datos de los widgets activos del usuario, en paralelo (el tiempo total es el del mas lento).
 * RBAC: un widget no permitido simplemente no aparece (sin error).
 */
@Slf4j
@Service
public class WidgetRegistryService {

    private final Map<String, DashboardWidget> widgets;
    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager txManager;
    private final String schema;
    private final ExecutorService executor = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "dashboard-widget");
        t.setDaemon(true);
        return t;
    });

    public WidgetRegistryService(List<DashboardWidget> widgets, JdbcTemplate jdbc, PlatformTransactionManager txManager,
                                 @Value("${app.db.schema}") String schema) {
        this.widgets = widgets.stream().collect(Collectors.toMap(w -> w.metadata().code(), w -> w, (a, b) -> a, LinkedHashMap::new));
        this.jdbc = jdbc;
        this.txManager = txManager;
        this.schema = schema;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void syncWidgets() {
        for (DashboardWidget w : widgets.values()) {
            DashboardWidget.Metadata m = w.metadata();
            String[] roles = m.allowedRoles().stream().map(Enum::name).sorted().toArray(String[]::new);
            jdbc.update("INSERT INTO " + schema + ".dashboard_widgets (code, title, description, category, category_order, widget_order, "
                            + "allowed_roles, is_system_active) VALUES (?, ?, ?, ?, ?, ?, ?, TRUE) ON CONFLICT (code) DO UPDATE SET "
                            + "title = EXCLUDED.title, description = EXCLUDED.description, category = EXCLUDED.category, "
                            + "category_order = EXCLUDED.category_order, widget_order = EXCLUDED.widget_order, "
                            + "allowed_roles = EXCLUDED.allowed_roles, is_system_active = TRUE, updated_at = now()",
                    m.code(), m.title(), m.description(), m.category(), m.categoryOrder(), m.widgetOrder(), roles);
        }
        String[] codes = widgets.keySet().toArray(String[]::new);
        int inactivos = jdbc.update("UPDATE " + schema + ".dashboard_widgets SET is_system_active = FALSE, updated_at = now() "
                + "WHERE is_system_active AND NOT (code = ANY (?))", (Object) codes);
        log.info("Widget Registry: {} widgets sincronizados, {} desactivados", codes.length, inactivos);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> available() {
        Role role = CurrentUser.role();
        Map<String, Map<String, Object>> prefs = new LinkedHashMap<>();
        jdbc.queryForList("SELECT w.code, p.is_enabled, p.position FROM " + schema + ".user_dashboard_preferences p JOIN " + schema
                + ".dashboard_widgets w ON w.id = p.widget_id WHERE p.user_id = ?", CurrentUser.id())
                .forEach(r -> prefs.put((String) r.get("code"), r));
        boolean sinPreferencias = prefs.isEmpty();
        List<Map<String, Object>> out = new ArrayList<>();
        widgets.values().stream()
                .map(DashboardWidget::metadata)
                .filter(m -> m.allowedRoles().contains(role))
                .sorted(Comparator.comparingInt(DashboardWidget.Metadata::categoryOrder).thenComparingInt(DashboardWidget.Metadata::widgetOrder))
                .forEach(m -> {
                    Map<String, Object> p = prefs.get(m.code());
                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("code", m.code());
                    r.put("title", m.title());
                    r.put("description", m.description());
                    r.put("category", m.category());
                    r.put("isEnabled", p != null ? p.get("is_enabled") : sinPreferencias && m.enabledByDefaultFor().contains(role));
                    r.put("position", p != null ? p.get("position") : m.categoryOrder() * 100 + m.widgetOrder());
                    out.add(r);
                });
        return out;
    }

    public record Preference(String widgetCode, boolean isEnabled, int position) {}

    @Transactional
    public List<Map<String, Object>> savePreferences(List<Preference> prefs) {
        Role role = CurrentUser.role();
        UUID me = CurrentUser.id();
        for (Preference p : prefs) {
            DashboardWidget w = widgets.get(p.widgetCode());
            if (w == null || !w.metadata().allowedRoles().contains(role)) {
                throw new BadRequestException("Widget no disponible: " + p.widgetCode());
            }
            jdbc.update("INSERT INTO " + schema + ".user_dashboard_preferences (user_id, widget_id, is_enabled, position) "
                            + "SELECT ?, id, ?, ? FROM " + schema + ".dashboard_widgets WHERE code = ? ON CONFLICT (user_id, widget_id) "
                            + "DO UPDATE SET is_enabled = EXCLUDED.is_enabled, position = EXCLUDED.position, updated_at = now()",
                    me, p.isEnabled(), p.position(), p.widgetCode());
        }
        return available();
    }

    /** Datos de los widgets activos del usuario, en paralelo, con su agencia y su usuario. */
    public Map<String, Object> data(Map<String, String> params) {
        // available() se llama desde la misma clase (sin proxy): se abre la transaccion a mano para que
        // haya SET LOCAL de la agencia; sin eso RLS esconde las preferencias guardadas.
        TransactionTemplate prefsTx = new TransactionTemplate(txManager);
        prefsTx.setReadOnly(true);
        List<String> activos = prefsTx.execute(s -> available()).stream()
                .filter(w -> Boolean.TRUE.equals(w.get("isEnabled")))
                .sorted(Comparator.comparingInt(w -> ((Number) w.get("position")).intValue()))
                .map(w -> (String) w.get("code")).toList();
        UUID agency = AgencyContext.require();
        SecurityContext security = SecurityContextHolder.getContext();
        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.setReadOnly(true);

        Map<String, CompletableFuture<Object>> futuros = new LinkedHashMap<>();
        for (String code : activos) {
            DashboardWidget w = widgets.get(code);
            futuros.put(code, CompletableFuture.supplyAsync(() -> {
                SecurityContextHolder.setContext(security);
                try {
                    return AgencyContext.callAs(agency, () -> tx.execute(s -> w.fetchData(agency, params)));
                } finally {
                    SecurityContextHolder.clearContext();
                }
            }, executor));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        futuros.forEach((code, f) -> {
            try {
                out.put(code, f.get(10, TimeUnit.SECONDS));
            } catch (Exception e) {
                log.warn("Widget {} fallo: {}", code, e.getMessage());
                out.put(code, Map.of("error", "No se pudieron cargar los datos"));
            }
        });
        return out;
    }

    Set<String> codes() {
        return widgets.keySet();
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
