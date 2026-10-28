package com.padilla.backend.reserva;

import com.padilla.backend.support.AbstractIntegrationTest;
import com.padilla.backend.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static com.padilla.backend.support.TestData.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** PRO-080 a PRO-084: leads por agencia, reservas (documentos, PDF, contrato) y reglas. */
@AutoConfigureMockMvc
class Sprint8IntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private TestData data;

    private UUID reserva(UUID propertyId, String status, String email) {
        UUID id = UUID.randomUUID();
        data.db().update("INSERT INTO propero_test.reservas (id, nombre, apellido, dni, propiedad, property_id, monto, status, email, "
                + "created_at, agency_id) VALUES (?, 'Ana', 'Perez', '30111222', 'Calle 1', ?, 150000, ?, ?, now(), ?)",
                id, propertyId, status, email, TestData.PADILLA);
        return id;
    }

    @Test
    void unaInmobiliariaSinModuloDeLeadsNoVeLosDePadilla() throws Exception {
        UUID agencia = data.agency("Sin leads");
        data.db().update("INSERT INTO propero_test.agency_settings (agency_id) VALUES (?)", agencia);
        UUID admin = data.userIn(agencia, "ADMIN");
        mvc.perform(get("/api/leads/portal").with(as(admin, "ADMIN"))).andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/api/sellers").with(as(admin, "ADMIN"))).andExpect(content().json("[]"));
        mvc.perform(get("/api/leads/stats/summary").with(as(admin, "ADMIN"))).andExpect(jsonPath("$.leadsEnabled").value(false));
        // Padilla tiene el modulo; NPS y tasaciones son tolerantes si la tabla no existe
        mvc.perform(get("/api/leads/satisfaction").with(as(data.user("ADMIN"), "ADMIN"))).andExpect(status().isOk());
    }

    @Test
    void propietarioVeLasReservasDeSusPropiedades() throws Exception {
        UUID owner = data.user("OWNER");
        UUID suya = reserva(data.property(owner, null), "PENDIENTE", null);
        UUID ajena = reserva(data.property(data.user("OWNER"), null), "PENDIENTE", null);
        mvc.perform(get("/api/reservas").with(as(owner, "OWNER")))
                .andExpect(jsonPath("$[?(@.id == '" + suya + "')]").exists())
                .andExpect(jsonPath("$[?(@.id == '" + ajena + "')]").doesNotExist());
        mvc.perform(get("/api/reservas/" + ajena).with(as(owner, "OWNER"))).andExpect(status().isNotFound());
    }

    @Test
    void documentosConLosDatosDeLaAgenciaYPdf() throws Exception {
        UUID admin = data.user("ADMIN");
        UUID r = reserva(data.property(data.user("OWNER"), null), "PENDIENTE", null);
        mvc.perform(get("/api/reservas/" + r + "/documents").with(as(admin, "ADMIN")))
                .andExpect(jsonPath("$.agencia.corredorMatricula").value("Mat. C.I.C.I.C.P.S.F. N° 829"));
        byte[] pdf = mvc.perform(get("/api/reservas/" + r + "/pdf/fc1").with(as(admin, "ADMIN")))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        mvc.perform(get("/api/reservas/" + r + "/pdf/xyz").with(as(admin, "ADMIN"))).andExpect(status().isBadRequest());
    }

    @Test
    void contratoDesdeUnaReservaAprobada() throws Exception {
        UUID admin = data.user("ADMIN");
        UUID owner = data.user("OWNER");
        UUID tenant = data.user("TENANT");
        String email = tenant + "@propero.test";
        UUID prop = data.property(owner, null);
        UUID pendiente = reserva(prop, "PENDIENTE", email);
        String body = "{\"startDate\":\"2026-11-01\",\"endDate\":\"2028-10-31\",\"monthlyAmount\":200000}";
        mvc.perform(post("/api/reservas/" + pendiente + "/contract").with(as(admin, "ADMIN")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
        UUID aprobada = reserva(prop, "APROBADA", email);
        mvc.perform(post("/api/reservas/" + aprobada + "/contract").with(as(admin, "ADMIN")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("ACTIVE"));
        assertThat(data.db().queryForMap("SELECT tenant_id, owner_id FROM propero_test.contracts WHERE property_id = ? AND status = 'ACTIVE'", prop))
                .containsEntry("tenant_id", tenant).containsEntry("owner_id", owner);
    }

    @Test
    void reglasDeLaAgencia() throws Exception {
        UUID agencia = data.agency("Reglas");
        data.db().update("INSERT INTO propero_test.agency_settings (agency_id) VALUES (?)", agencia);
        UUID sa = data.userIn(agencia, "SUPER_ADMIN");
        UUID admin = data.userIn(agencia, "ADMIN");
        mvc.perform(get("/api/settings/rules").with(as(admin, "ADMIN")))
                .andExpect(jsonPath("$.slaHoras").value(48))
                .andExpect(jsonPath("$.tiposObligatorios").isArray());
        mvc.perform(put("/api/settings/rules").with(as(admin, "ADMIN")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"slaHoras\":24}")).andExpect(status().isForbidden());
        mvc.perform(put("/api/settings/rules").with(as(sa, "SUPER_ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slaHoras\":24,\"montoCritico\":500000}"))
                .andExpect(jsonPath("$.slaHoras").value(24)).andExpect(jsonPath("$.montoCritico").value(500000));
    }
}
