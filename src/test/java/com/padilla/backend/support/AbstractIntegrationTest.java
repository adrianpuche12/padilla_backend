package com.padilla.backend.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * PRO-004: base para tests de integracion. Un unico Postgres 17 en Docker para toda la corrida
 * (patron singleton container); Flyway crea el schema propero_test y Hibernate lo valida.
 *
 * PRO-021: igual que en los ambientes reales, Flyway migra con el superusuario y la app se conecta
 * con el rol propero_app (sin superusuario), asi las politicas RLS se prueban de verdad.
 * TestData usa el superusuario para cargar datos de cualquier agencia.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestData.class)
public abstract class AbstractIntegrationTest {

    public static final String APP_USER = "propero_app";
    public static final String APP_PASSWORD = "propero_app_test";

    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("propero")
                    .withUsername("propero")
                    .withPassword("propero");

    static {
        POSTGRES.start();
        try (Connection c = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement s = c.createStatement()) {
            String create = "CREATE ROLE " + APP_USER + " LOGIN PASSWORD '" + APP_PASSWORD + "' NOSUPERUSER NOBYPASSRLS";
            s.execute("DO $do$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '" + APP_USER + "') THEN "
                    + "EXECUTE '" + create.replace("'", "''") + "'; END IF; END $do$");
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo crear el rol de la app en el Postgres de test", e);
        }
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> APP_USER);
        registry.add("spring.datasource.password", () -> APP_PASSWORD);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("legacy.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("legacy.datasource.username", POSTGRES::getUsername);
        registry.add("legacy.datasource.password", POSTGRES::getPassword);
    }
}
