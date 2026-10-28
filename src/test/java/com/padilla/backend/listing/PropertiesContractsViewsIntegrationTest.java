package com.padilla.backend.listing;

import com.padilla.backend.support.AbstractIntegrationTest;
import com.padilla.backend.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static com.padilla.backend.support.TestData.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** PRO-041 / PRO-042 / PRO-043. */
@AutoConfigureMockMvc
class PropertiesContractsViewsIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private TestData data;

    private UUID proveedor(String especialidad) {
        UUID id = data.user("PROVIDER");
        data.db().update("INSERT INTO propero_test.client_profiles (user_id, persona_type, specialty, agency_id) VALUES (?, 'FISICA', ?, ?)",
                id, especialidad, TestData.PADILLA);
        return id;
    }

    @Test
    void proveedoresSugeridosSegunLaCategoria() throws Exception {
        UUID admin = data.user("ADMIN");
        UUID owner = data.user("OWNER");
        UUID tenant = data.user("TENANT");
        UUID plomero = proveedor("PLOMERO");
        UUID pintor = proveedor("PINTOR");
        UUID ticket = data.ticket(data.property(owner, tenant), tenant, "ABIERTA"); // categoria PLOMERIA

        mvc.perform(get("/api/tickets/" + ticket + "/suggested-providers").with(as(admin, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + plomero + "')]").exists())
                .andExpect(jsonPath("$[?(@.id == '" + pintor + "')]").doesNotExist());
    }

    @Test
    void misPropiedadesYMisContratos() throws Exception {
        UUID owner = data.user("OWNER");
        UUID tenant = data.user("TENANT");
        UUID prop = data.property(owner, tenant);
        data.activeContract(prop, owner, tenant);
        data.property(data.user("OWNER"), null);

        mvc.perform(get("/api/me/properties").with(as(owner, "OWNER")))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(prop.toString()));
        mvc.perform(get("/api/me/properties").with(as(tenant, "TENANT")))
                .andExpect(jsonPath("$[0].id").value(prop.toString()));
        mvc.perform(get("/api/me/contracts").with(as(tenant, "TENANT")))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].propertyAddress").exists());
        mvc.perform(get("/api/me/contracts").with(as(data.user("PROVIDER"), "PROVIDER"))).andExpect(status().isForbidden());
    }

    @Test
    void desactivarPropiedadConservaElEstadoYNoSePuedeConContratoVigente() throws Exception {
        UUID admin = data.user("ADMIN");
        UUID owner = data.user("OWNER");
        UUID tenant = data.user("TENANT");
        UUID alquilada = data.property(owner, tenant);
        data.activeContract(alquilada, owner, tenant);
        UUID libre = data.property(owner, null);

        mvc.perform(patch("/api/properties/" + alquilada + "/deactivate").with(as(admin, "ADMIN"))).andExpect(status().isConflict());
        mvc.perform(patch("/api/properties/" + libre + "/deactivate").with(as(admin, "ADMIN"))).andExpect(status().isOk());
        assertThat(data.db().queryForMap("SELECT active, status FROM propero_test.properties WHERE id = ?", libre))
                .containsEntry("active", false).containsEntry("status", "AVAILABLE");
        // alias DELETE sigue funcionando
        mvc.perform(patch("/api/properties/" + libre + "/reactivate").with(as(admin, "ADMIN"))).andExpect(status().isOk());
        mvc.perform(delete("/api/properties/" + libre).with(as(admin, "ADMIN"))).andExpect(status().isOk());
    }

    @Test
    void rescindirContratoPorPatch() throws Exception {
        UUID admin = data.user("ADMIN");
        UUID owner = data.user("OWNER");
        UUID tenant = data.user("TENANT");
        UUID prop = data.property(owner, tenant);
        UUID contrato = data.activeContract(prop, owner, tenant);
        mvc.perform(patch("/api/contracts/" + contrato + "/terminate").with(as(admin, "ADMIN"))).andExpect(status().isOk());
        assertThat(data.db().queryForObject("SELECT status FROM propero_test.contracts WHERE id = ?", String.class, contrato))
                .isEqualTo("TERMINATED");
    }
}
