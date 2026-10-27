package com.padilla.backend.ticket;

import com.padilla.backend.support.AbstractIntegrationTest;
import com.padilla.backend.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static com.padilla.backend.support.TestData.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** PRO-011 / PRO-012 / PRO-015: flujo de tickets contra Postgres real. */
@AutoConfigureMockMvc
class TicketFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private TestData data;
    @Autowired private JdbcTemplate jdbc;

    private UUID admin, owner, tenant, provider, property;

    @BeforeEach
    void datos() {
        admin = data.user("ADMIN");
        owner = data.user("OWNER");
        tenant = data.user("TENANT");
        provider = data.user("PROVIDER");
        property = data.property(owner, tenant);
        data.activeContract(property, owner, tenant);
    }

    private static String body(UUID propertyId, UUID tenantId) {
        return "{\"title\":\"Pierde agua\",\"description\":\"El baño pierde\",\"category\":\"PLOMERIA\",\"propertyId\":\""
                + propertyId + "\"" + (tenantId != null ? ",\"tenantId\":\"" + tenantId + "\"" : "") + "}";
    }

    // --- PRO-012: alta ---

    @Test
    void adminCreaTicketYSeTomaElInquilinoDelContrato() throws Exception {
        mvc.perform(post("/api/tickets").with(as(admin, "ADMIN")).contentType(MediaType.APPLICATION_JSON).content(body(property, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").value(tenant.toString()))
                .andExpect(jsonPath("$.status").value("ABIERTA"));
    }

    @Test
    void adminNoPuedeAsignarElTicketAUnNoInquilino() throws Exception {
        mvc.perform(post("/api/tickets").with(as(admin, "ADMIN")).contentType(MediaType.APPLICATION_JSON).content(body(property, owner)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void inquilinoCreaTicketDeSuViviendaPeroNoDeOtra() throws Exception {
        mvc.perform(post("/api/tickets").with(as(tenant, "TENANT")).contentType(MediaType.APPLICATION_JSON).content(body(property, null)))
                .andExpect(status().isCreated());
        UUID otra = data.property(owner, null);
        mvc.perform(post("/api/tickets").with(as(tenant, "TENANT")).contentType(MediaType.APPLICATION_JSON).content(body(otra, null)))
                .andExpect(status().isForbidden());
    }

    // --- PRO-011: asignar proveedor ---

    @Test
    void asignarProveedorRegistraElCambioYPasaAEnProceso() throws Exception {
        UUID ticket = data.ticket(property, tenant, "ABIERTA");
        mvc.perform(patch("/api/tickets/" + ticket + "/assign").param("providerId", provider.toString()).with(as(admin, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EN_PROCESO"))
                .andExpect(jsonPath("$.providerId").value(provider.toString()));
        UUID changedBy = jdbc.queryForObject(
                "SELECT changed_by FROM propero_test.ticket_provider_log WHERE ticket_id = ?", UUID.class, ticket);
        assertThat(changedBy).isEqualTo(admin);
    }

    @Test
    void asignarConAdminSoloDeKeycloakNoDa500() throws Exception {
        UUID ticket = data.ticket(property, tenant, "ABIERTA");
        UUID adminSoloKeycloak = UUID.randomUUID();
        mvc.perform(patch("/api/tickets/" + ticket + "/assign").param("providerId", provider.toString())
                        .with(as(adminSoloKeycloak, "ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void asignarProveedorInexistenteOQueNoEsProveedor() throws Exception {
        UUID ticket = data.ticket(property, tenant, "ABIERTA");
        mvc.perform(patch("/api/tickets/" + ticket + "/assign").param("providerId", UUID.randomUUID().toString()).with(as(admin, "ADMIN")))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/tickets/" + ticket + "/assign").param("providerId", tenant.toString()).with(as(admin, "ADMIN")))
                .andExpect(status().isBadRequest());
        data.deactivate(provider);
        mvc.perform(patch("/api/tickets/" + ticket + "/assign").param("providerId", provider.toString()).with(as(admin, "ADMIN")))
                .andExpect(status().isBadRequest());
    }

    // --- PRO-012: maquina de estados ---

    private void cambiar(UUID ticket, UUID quien, String rol, String estado, int esperado) throws Exception {
        mvc.perform(patch("/api/tickets/" + ticket + "/status").with(as(quien, rol)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"" + estado + "\",\"rejectionReason\":\"motivo\"}"))
                .andExpect(status().is(esperado));
    }

    @Test
    void recorridoCompletoYTransicionesInvalidas() throws Exception {
        UUID ticket = data.ticket(property, tenant, "ABIERTA");
        cambiar(ticket, admin, "ADMIN", "RESUELTA", 409);   // no se salta EN_PROCESO
        cambiar(ticket, admin, "ADMIN", "EN_PROCESO", 200);
        cambiar(ticket, admin, "ADMIN", "RESUELTA", 200);
        cambiar(ticket, admin, "ADMIN", "CERRADA", 200);
        cambiar(ticket, admin, "ADMIN", "EN_PROCESO", 409); // CERRADA es final
        Boolean cerrado = jdbc.queryForObject(
                "SELECT closed_at IS NOT NULL FROM propero_test.tickets WHERE id = ?", Boolean.class, ticket);
        assertThat(cerrado).isTrue();
    }

    @Test
    void inquilinoCancelaSoloMientrasEstaAbierto() throws Exception {
        UUID abierto = data.ticket(property, tenant, "ABIERTA");
        cambiar(abierto, tenant, "TENANT", "CANCELADA", 200);
        UUID tomado = data.ticket(property, tenant, "EN_PROCESO");
        cambiar(tomado, tenant, "TENANT", "CANCELADA", 403);
        cambiar(tomado, tenant, "TENANT", "CERRADA", 403);
    }

    @Test
    void rechazarExigeMotivo() throws Exception {
        UUID ticket = data.ticket(property, tenant, "ABIERTA");
        mvc.perform(patch("/api/tickets/" + ticket + "/reject").param("reason", " ").with(as(admin, "ADMIN")))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/tickets/" + ticket + "/reject").param("reason", "No corresponde").with(as(admin, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECHAZADA"));
    }

    // --- PRO-015: el propietario ve los tickets de sus propiedades ---

    @Test
    void propietarioVeLosTicketsDeSusPropiedadesYNoLosAjenos() throws Exception {
        UUID suyo = data.ticket(property, tenant, "ABIERTA");
        UUID otroOwner = data.user("OWNER");
        UUID ajena = data.property(otroOwner, null);
        UUID ajeno = data.ticket(ajena, tenant, "ABIERTA");

        mvc.perform(get("/api/tickets").with(as(owner, "OWNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + suyo + "')]").exists())
                .andExpect(jsonPath("$[?(@.id == '" + ajeno + "')]").doesNotExist());
        mvc.perform(get("/api/tickets/" + suyo).with(as(owner, "OWNER"))).andExpect(status().isOk());
        mvc.perform(get("/api/tickets/" + ajeno).with(as(owner, "OWNER"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/tickets/" + suyo + "/comments").with(as(owner, "OWNER"))).andExpect(status().isOk());
    }
}
