package com.padilla.backend;

import com.padilla.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PRO-003 / PRO-004: el contexto levanta contra un Postgres vacio, Flyway aplica las migraciones
 * y Hibernate valida que las entidades coincidan con el schema.
 */
class PadillaBackendApplicationTests extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextLoads() {
    }

    @Test
    void flywayAplicoLasMigracionesEnElSchemaDelAmbiente() {
        Integer aplicadas = jdbcTemplate.queryForObject(
                "select count(*) from propero_test.flyway_schema_history where success", Integer.class);
        Integer tablas = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'propero_test'"
                        + " and table_name in ('users','properties','contracts','tickets','reservas')", Integer.class);
        assertThat(aplicadas).isGreaterThanOrEqualTo(1);
        assertThat(tablas).isEqualTo(5);
    }
}
