package com.padilla.backend.user;

import com.padilla.backend.service.auth.KeycloakAdminService;
import com.padilla.backend.service.auth.KeycloakAuthService;
import com.padilla.backend.service.email.EmailService;
import com.padilla.backend.support.AbstractIntegrationTest;
import com.padilla.backend.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpClientErrorException;

import java.util.Map;
import java.util.UUID;

import static com.padilla.backend.support.TestData.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** PRO-031 / PRO-033 / PRO-034: perfil propio, baja logica con efectos y reasignacion. */
@AutoConfigureMockMvc
class UserLifecycleIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private TestData data;
    @MockitoBean private KeycloakAdminService keycloakAdmin;
    @MockitoBean private KeycloakAuthService keycloakAuth;
    @MockitoBean private EmailService emailService;

    private Map<String, Object> fila(String sql, UUID id) {
        return data.db().queryForMap(sql, id);
    }

    @Test
    void bajaDelInquilinoCancelaSusTicketsAbiertosYMarcaElContrato() throws Exception {
        UUID admin = data.user("ADMIN");
        UUID owner = data.user("OWNER");
        UUID tenant = data.user("TENANT");
        UUID prop = data.property(owner, tenant);
        UUID contrato = data.activeContract(prop, owner, tenant);
        UUID abierto = data.ticket(prop, tenant, "ABIERTA");
        UUID cerrado = data.ticket(prop, tenant, "CERRADA");

        mvc.perform(delete("/api/clients/" + tenant).with(as(admin, "ADMIN"))).andExpect(status().isNoContent());

        assertThat(fila("SELECT active, deactivated_by FROM propero_test.users WHERE id = ?", tenant))
                .containsEntry("active", false).containsEntry("deactivated_by", admin);
        assertThat(fila("SELECT status, rejection_reason FROM propero_test.tickets WHERE id = ?", abierto))
                .containsEntry("status", "CANCELADA");
        assertThat(fila("SELECT status FROM propero_test.tickets WHERE id = ?", cerrado)).containsEntry("status", "CERRADA");
        assertThat(fila("SELECT tenant_inactive, status FROM propero_test.contracts WHERE id = ?", contrato))
                .containsEntry("tenant_inactive", true).containsEntry("status", "ACTIVE");
        verify(keycloakAdmin, never()).deleteUserByEmail(any());

        mvc.perform(patch("/api/clients/" + tenant + "/reactivate").with(as(admin, "ADMIN"))).andExpect(status().isOk());
        assertThat(fila("SELECT tenant_inactive FROM propero_test.contracts WHERE id = ?", contrato))
                .containsEntry("tenant_inactive", false);
    }

    @Test
    void bajaDelProveedorDevuelveSusTicketsALaCola() throws Exception {
        UUID admin = data.user("ADMIN");
        UUID owner = data.user("OWNER");
        UUID tenant = data.user("TENANT");
        UUID provider = data.user("PROVIDER");
        UUID prop = data.property(owner, tenant);
        UUID ticket = data.ticket(prop, tenant, "EN_PROCESO");
        data.db().update("UPDATE propero_test.tickets SET provider_id = ? WHERE id = ?", provider, ticket);

        mvc.perform(patch("/api/clients/" + provider + "/deactivate").with(as(admin, "ADMIN"))).andExpect(status().isOk());

        Map<String, Object> t = fila("SELECT provider_id, status FROM propero_test.tickets WHERE id = ?", ticket);
        assertThat(t.get("provider_id")).isNull();
        assertThat(t.get("status")).isEqualTo("ABIERTA");
        Integer logs = data.db().queryForObject(
                "SELECT count(*) FROM propero_test.ticket_provider_log WHERE ticket_id = ? AND old_provider_id = ? AND new_provider_id IS NULL",
                Integer.class, ticket, provider);
        assertThat(logs).isEqualTo(1);
    }

    @Test
    void bajaDeUnAdminReasignaSusActoresYElBorradoDefinitivoEsSoloDelSuperAdmin() throws Exception {
        UUID manager = data.user("MANAGER");
        UUID adminSaliente = data.user("ADMIN");
        UUID adminQueQueda = data.user("ADMIN");
        UUID tenant = data.user("TENANT");
        data.db().update("UPDATE propero_test.users SET created_by = ? WHERE id = ?", adminSaliente, tenant);

        mvc.perform(delete("/api/users/" + adminSaliente).with(as(manager, "MANAGER"))).andExpect(status().isOk());
        UUID gestor = data.db().queryForObject("SELECT created_by FROM propero_test.users WHERE id = ?", UUID.class, tenant);
        assertThat(gestor).isNotNull().isNotEqualTo(adminSaliente);

        mvc.perform(delete("/api/clients/" + tenant + "/permanent").with(as(manager, "MANAGER"))).andExpect(status().isForbidden());
    }

    @Test
    void reasignacionManualEntreAdmins() throws Exception {
        UUID manager = data.user("MANAGER");
        UUID a1 = data.user("ADMIN");
        UUID a2 = data.user("ADMIN");
        UUID owner = data.user("OWNER");
        data.db().update("UPDATE propero_test.users SET created_by = ? WHERE id = ?", a1, owner);

        mvc.perform(patch("/api/users/" + a1 + "/reassign").param("to", a2.toString()).with(as(a1, "ADMIN")))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/users/" + a1 + "/reassign").param("to", owner.toString()).with(as(manager, "MANAGER")))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/users/" + a1 + "/reassign").param("to", a2.toString()).with(as(manager, "MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reasignados").value(1));
        assertThat(data.db().queryForObject("SELECT created_by FROM propero_test.users WHERE id = ?", UUID.class, owner)).isEqualTo(a2);
    }

    @Test
    void miPerfilParaUnInquilino() throws Exception {
        UUID tenant = data.user("TENANT");
        data.db().update("INSERT INTO propero_test.client_profiles (user_id, persona_type, address, agency_id) VALUES (?, 'FISICA', 'Viejo 1', ?)",
                tenant, TestData.PADILLA);

        mvc.perform(get("/api/me").with(as(tenant, "TENANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("TENANT"))
                .andExpect(jsonPath("$.agencyName").value("Inmobiliaria Padilla"))
                .andExpect(jsonPath("$.address").value("Viejo 1"));
        mvc.perform(put("/api/me").with(as(tenant, "TENANT")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nuevo Nombre\",\"phone\":\"341555\",\"address\":\"Nuevo 2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Nuevo Nombre"))
                .andExpect(jsonPath("$.address").value("Nuevo 2"));
    }

    @Test
    void cambioDeContrasenaPropioExigeLaActual() throws Exception {
        UUID owner = data.user("OWNER");
        when(keycloakAuth.login(argThat(r -> r != null && "Mala123!".equals(r.getPassword()))))
                .thenThrow(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "x", null, null, null));

        mvc.perform(put("/api/me/password").with(as(owner, "OWNER")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Mala123!\",\"newPassword\":\"Nueva123!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La contraseña actual no es correcta"));
        mvc.perform(put("/api/me/password").with(as(owner, "OWNER")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Buena123!\",\"newPassword\":\"corta\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/me/password").with(as(owner, "OWNER")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Buena123!\",\"newPassword\":\"Nueva123!\"}"))
                .andExpect(status().isOk());
        verify(keycloakAdmin).setPermanentPasswordById(eq(owner.toString()), eq("Nueva123!"));
    }
}
