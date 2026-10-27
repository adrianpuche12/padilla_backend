package com.padilla.backend.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * PRO-004: base para tests de integracion. Un unico Postgres 17 en Docker para toda la corrida
 * (patron singleton container); Flyway crea el schema propero_test y Hibernate lo valida.
 * La base legacy apunta al mismo contenedor (schema legacy_test).
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("propero")
                    .withUsername("propero")
                    .withPassword("propero");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("legacy.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("legacy.datasource.username", POSTGRES::getUsername);
        registry.add("legacy.datasource.password", POSTGRES::getPassword);
    }
}
