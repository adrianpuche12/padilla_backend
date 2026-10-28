package com.padilla.backend.saas;

import com.padilla.backend.enums.Role;
import com.padilla.backend.service.auth.KeycloakAdminService;
import com.padilla.backend.service.email.EmailService;
import com.padilla.backend.support.AbstractIntegrationTest;
import com.padilla.backend.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static com.padilla.backend.support.TestData.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** PRO-023: alta automatica de una inmobiliaria con su SUPER_ADMIN (Keycloak simulado). */
@AutoConfigureMockMvc
class AgencyOnboardingIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private TestData data;
    @MockitoBean private KeycloakAdminService keycloak;
    @MockitoBean private EmailService emailService;

    private static final String BODY = "{\"name\":\"Inmobiliaria Este\",\"slug\":\"este\",\"plan\":\"PRO\","
            + "\"adminName\":\"Marta Este\",\"adminEmail\":\"Marta@Este.test\"}";

    @Test
    void altaCompletaSinPasosManuales() throws Exception {
        UUID kcId = UUID.randomUUID();
        when(keycloak.createUser(anyString(), eq("marta@este.test"), eq(Role.SUPER_ADMIN))).thenReturn(kcId.toString());
        when(keycloak.generateAndSetTemporaryPassword(kcId.toString())).thenReturn("Temp1234!");

        mvc.perform(post("/api/platform/agencies").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.agency.slug").value("este"))
                .andExpect(jsonPath("$.temporaryPassword").value("Temp1234!"));

        Map<String, Object> admin = data.db().queryForMap(
                "SELECT u.role, u.first_login, a.slug FROM propero_test.users u JOIN propero_test.agencies a ON a.id = u.agency_id WHERE u.id = ?", kcId);
        assertThat(admin.get("role")).isEqualTo("SUPER_ADMIN");
        assertThat(admin.get("first_login")).isEqualTo(true);
        assertThat(admin.get("slug")).isEqualTo("este");
        verify(emailService).sendWelcomeEmail("marta@este.test", "Marta Este", "Temp1234!");

        // El SUPER_ADMIN nuevo entra a su agencia y ve su configuracion (vacia, con el nombre)
        mvc.perform(get("/api/settings/agency").with(as(kcId, "SUPER_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agencyName").value("Inmobiliaria Este"))
                .andExpect(jsonPath("$.plan").value("PRO"));
        // Y no ve los tickets de Padilla
        mvc.perform(get("/api/tickets").with(as(kcId, "SUPER_ADMIN"))).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void soloPersonalDePlataformaYSlugUnico() throws Exception {
        mvc.perform(post("/api/platform/agencies").with(as(data.user("SUPER_ADMIN"), "SUPER_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/platform/agencies").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("\"este\"", "\"padilla\"").replace("Marta@Este.test", "otra@este.test")))
                .andExpect(status().isConflict());
    }
}
