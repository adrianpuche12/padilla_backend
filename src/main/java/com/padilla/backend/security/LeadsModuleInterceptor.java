package com.padilla.backend.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * PRO-080: los endpoints de leads leen la base legacy (n8n) de Padilla, que no esta separada por agencia.
 * Si la agencia del usuario no tiene el modulo habilitado (agency_settings.leads_enabled) se responde
 * vacio: [] en los listados, {"leadsEnabled": false} en estadisticas y conteos. Asi una inmobiliaria
 * nueva nunca ve leads de otra.
 */
@Component
@RequiredArgsConstructor
public class LeadsModuleInterceptor implements HandlerInterceptor {

    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager txManager;

    @Value("${app.db.schema}")
    private String schema;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        UUID agency = AgencyContext.current().orElse(null);
        if (agency != null && enabled(agency)) {
            return true;
        }
        String path = request.getRequestURI();
        boolean objeto = path.contains("/stats") || path.endsWith("/count") || request.getParameter("page") != null;
        response.setStatus(200);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objeto
                ? "{\"leadsEnabled\":false,\"content\":[],\"totalElements\":0,\"totalPages\":0,\"page\":0,\"size\":0}"
                : "[]");
        return false;
    }

    private boolean enabled(UUID agency) {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.setReadOnly(true);
        List<Boolean> r = AgencyContext.callAs(agency, () -> tx.execute(s ->
                jdbc.queryForList("SELECT leads_enabled FROM " + schema + ".agency_settings WHERE agency_id = ?", Boolean.class, agency)));
        return r != null && !r.isEmpty() && Boolean.TRUE.equals(r.get(0));
    }
}
