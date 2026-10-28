package com.padilla.backend.ticket;

import com.padilla.backend.support.AbstractIntegrationTest;
import com.padilla.backend.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.UUID;

import static com.padilla.backend.support.TestData.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** PRO-051 / PRO-053 / PRO-054: metadata de carga, presupuestos con escalamiento, visita y costo final. */
@AutoConfigureMockMvc
class TicketExtrasIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private TestData data;

    private UUID admin, manager, owner, tenant, provider, property, ticket;

    @BeforeEach
    void datos() {
        admin = data.user("ADMIN");
        manager = data.user("MANAGER");
        owner = data.user("OWNER");
        tenant = data.user("TENANT");
        provider = data.user("PROVIDER");
        property = data.property(owner, tenant);
        data.activeContract(property, owner, tenant);
        ticket = data.ticket(property, tenant, "EN_PROCESO");
        data.db().update("UPDATE propero_test.tickets SET provider_id = ? WHERE id = ?", provider, ticket);
    }

    private String presupuesto(UUID quien, String rol, int monto, int esperado) throws Exception {
        return mvc.perform(post("/api/tickets/" + ticket + "/budgets").with(as(quien, rol)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monto\":" + monto + ",\"detalle\":\"Cambio de cuerito y mano de obra\"}"))
                .andExpect(status().is(esperado)).andReturn().getResponse().getContentAsString();
    }

    private static String id(String json) {
        return json.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
    }

    @Test
    void metadataDelModoDeCarga() throws Exception {
        mvc.perform(post("/api/tickets").with(as(tenant, "TENANT")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Luz\",\"description\":\"No anda\",\"category\":\"ELECTRICIDAD\",\"propertyId\":\"" + property
                                + "\",\"modoCarga\":\"VOZ_GUIADA\",\"tiempoCargaSegundos\":95,\"dispositivo\":\"Android\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.modoCarga").value("VOZ_GUIADA"))
                .andExpect(jsonPath("$.tiempoCargaSegundos").value(95));
    }

    @Test
    void presupuestoLoEnviaSoloElProveedorYLoApruebaElPropietario() throws Exception {
        presupuesto(data.user("PROVIDER"), "PROVIDER", 1000, 404);
        String b = presupuesto(provider, "PROVIDER", 15000, 201);
        assertThat(data.db().queryForObject("SELECT count(*) FROM propero_test.notification_log WHERE user_id = ? AND tipo = 'PRESUPUESTO_RECIBIDO'",
                Integer.class, owner)).isEqualTo(1);
        mvc.perform(get("/api/tickets/" + ticket + "/budgets").with(as(tenant, "TENANT"))).andExpect(status().isForbidden());
        mvc.perform(patch("/api/budgets/" + id(b) + "/approve").with(as(owner, "OWNER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APROBADO"));
        mvc.perform(patch("/api/budgets/" + id(b) + "/approve").with(as(admin, "ADMIN"))).andExpect(status().isConflict());
        assertThat(data.db().queryForObject("SELECT count(*) FROM propero_test.notification_log WHERE user_id = ? AND tipo = 'PRESUPUESTO_APROBADO'",
                Integer.class, provider)).isEqualTo(1);
    }

    @Test
    void presupuestoSobreElMontoCriticoEscalaAlManager() throws Exception {
        data.db().update("UPDATE propero_test.agency_settings SET monto_critico = 100000 WHERE agency_id = ?", TestData.PADILLA);
        try {
            String b = presupuesto(provider, "PROVIDER", 250000, 201);
            assertThat(b).contains("\"requiereManager\":true");
            mvc.perform(patch("/api/budgets/" + id(b) + "/approve").with(as(admin, "ADMIN"))).andExpect(status().isForbidden());
            mvc.perform(patch("/api/budgets/" + id(b) + "/reject").with(as(manager, "MANAGER")).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"motivo\":\" \"}")).andExpect(status().isBadRequest());
            mvc.perform(patch("/api/budgets/" + id(b) + "/approve").with(as(manager, "MANAGER")))
                    .andExpect(jsonPath("$.status").value("APROBADO"));
            assertThat(data.db().queryForObject("SELECT count(*) FROM propero_test.notification_log WHERE user_id = ? AND tipo = 'TICKET_ESCALADO'",
                    Integer.class, manager)).isEqualTo(1);
        } finally {
            data.db().update("UPDATE propero_test.agency_settings SET monto_critico = NULL WHERE agency_id = ?", TestData.PADILLA);
        }
    }

    @Test
    void visitaCoordinadaYCostoFinal() throws Exception {
        OffsetDateTime manana = OffsetDateTime.now().plusDays(1).withNano(0);
        mvc.perform(patch("/api/tickets/" + ticket + "/schedule").with(as(provider, "PROVIDER")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledAt\":\"" + manana + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.scheduledAt").exists());
        assertThat(data.db().queryForObject("SELECT count(*) FROM propero_test.notification_log WHERE user_id = ? AND titulo LIKE 'Visita coordinada%'",
                Integer.class, tenant)).isEqualTo(1);
        mvc.perform(patch("/api/tickets/" + ticket + "/schedule").with(as(provider, "PROVIDER")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"scheduledAt\":\"" + manana.minusDays(5) + "\"}")).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/tickets/" + ticket + "/cost").with(as(provider, "PROVIDER")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"costoFinal\":18500.50}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.costoFinal").value(18500.50));
        mvc.perform(patch("/api/tickets/" + ticket + "/cost").with(as(data.user("PROVIDER"), "PROVIDER")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"costoFinal\":1}")).andExpect(status().isForbidden());
    }
}
