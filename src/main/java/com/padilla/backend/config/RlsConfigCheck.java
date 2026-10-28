package com.padilla.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * PRO-021: RLS no aplica a superusuarios ni a roles con BYPASSRLS. Si la app se conecta asi, el
 * aislamiento entre inmobiliarias queda desactivado: se avisa fuerte en el log al arrancar.
 */
@Slf4j
@Component
public class RlsConfigCheck implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    public RlsConfigCheck(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        Boolean bypasses = jdbc.queryForObject(
                "SELECT rolsuper OR rolbypassrls FROM pg_roles WHERE rolname = current_user", Boolean.class);
        String user = jdbc.queryForObject("SELECT current_user", String.class);
        if (Boolean.TRUE.equals(bypasses)) {
            log.warn("=== RLS NO ACTIVO: la app se conecta como '{}' (superusuario o BYPASSRLS). "
                    + "El aislamiento entre inmobiliarias depende solo del codigo. Configurar DB_APP_USERNAME=propero_app ===", user);
        } else {
            log.info("RLS activo: la app se conecta como '{}'", user);
        }
    }

    public boolean rlsActive() {
        return !Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT rolsuper OR rolbypassrls FROM pg_roles WHERE rolname = current_user", Boolean.class));
    }
}
