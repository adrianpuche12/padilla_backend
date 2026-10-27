package com.padilla.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PRO-002: la base principal mapea solo las entidades propias; las legacy van a su propio EntityManager.
 */
class DataSourceConfigTest {

    @Test
    void entidadesPrincipalesExcluyenLegacy() {
        List<String> clases = DataSourceConfig.mainManagedTypes(new DefaultResourceLoader())
                .getManagedClassNames();

        assertThat(clases).contains("com.padilla.backend.entity.User", "com.padilla.backend.entity.Ticket");
        assertThat(clases).noneMatch(c -> c.startsWith("com.padilla.backend.entity.legacy."));
    }
}
