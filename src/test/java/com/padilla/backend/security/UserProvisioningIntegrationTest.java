package com.padilla.backend.security;

import com.padilla.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PRO-005: el usuario demo de Peter existe en Keycloak pero no en users. Despues del primer request
 * autenticado tiene que existir (si no, reservas y asignacion de proveedor daban 500).
 */
@AutoConfigureMockMvc
class UserProvisioningIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private com.padilla.backend.support.TestData data;

    @Autowired
    private MockMvc mvc;

    @Test
    void usuarioSoloDeKeycloakQuedaCreadoEnUsers() throws Exception {
        UUID sub = UUID.randomUUID();
        mvc.perform(get("/api/tickets").with(jwt()
                        .jwt(j -> j.subject(sub.toString()).claim("email", "Demo.Admin@Propero.test").claim("name", "Demo Admin"))
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());

        Map<String, Object> fila = data.db().queryForMap(
                "SELECT email, name, role, active, first_login FROM propero_test.users WHERE id = ?", sub);
        assertThat(fila.get("email")).isEqualTo("demo.admin@propero.test");
        assertThat(fila.get("name")).isEqualTo("Demo Admin");
        assertThat(fila.get("role")).isEqualTo("ADMIN");
        assertThat(fila.get("active")).isEqualTo(true);
        assertThat(fila.get("first_login")).isEqualTo(false);
    }

    @Test
    void requestRepetidoNoDuplica() throws Exception {
        UUID sub = UUID.randomUUID();
        for (int i = 0; i < 3; i++) {
            mvc.perform(get("/api/tickets").with(jwt()
                            .jwt(j -> j.subject(sub.toString()).claim("email", "tenant-" + sub + "@propero.test"))
                            .authorities(new SimpleGrantedAuthority("ROLE_TENANT"))))
                    .andExpect(status().isOk());
        }
        Integer filas = data.db().queryForObject("SELECT count(*) FROM propero_test.users WHERE id = ?", Integer.class, sub);
        assertThat(filas).isEqualTo(1);
    }
}
