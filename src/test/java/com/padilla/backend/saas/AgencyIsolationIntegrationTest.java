package com.padilla.backend.saas;

import com.padilla.backend.config.RlsConfigCheck;
import com.padilla.backend.security.AgencyContext;
import com.padilla.backend.support.AbstractIntegrationTest;
import com.padilla.backend.support.TestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static com.padilla.backend.support.TestData.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PRO-020 / PRO-021 / PRO-022: una inmobiliaria nunca ve ni toca datos de otra.
 * Corre con el rol propero_app (sin superusuario), asi que las politicas RLS se ejercitan de verdad.
 */
@AutoConfigureMockMvc
class AgencyIsolationIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private TestData data;
    @Autowired private JdbcTemplate appJdbc;
    @Autowired private PlatformTransactionManager txManager;
    @Autowired private RlsConfigCheck rlsConfigCheck;

    private UUID agenciaB, adminA, adminB, ticketA, ticketB, propiedadA, propiedadB, inquilinoB, proveedorB;

    @BeforeEach
    void dosInmobiliarias() {
        agenciaB = data.agency("Inmobiliaria B");
        adminA = data.user("ADMIN");
        UUID ownerA = data.user("OWNER");
        UUID tenantA = data.user("TENANT");
        propiedadA = data.property(ownerA, tenantA);
        ticketA = data.ticket(propiedadA, tenantA, "ABIERTA");

        adminB = data.userIn(agenciaB, "ADMIN");
        UUID ownerB = data.userIn(agenciaB, "OWNER");
        inquilinoB = data.userIn(agenciaB, "TENANT");
        proveedorB = data.userIn(agenciaB, "PROVIDER");
        propiedadB = data.propertyIn(agenciaB, ownerB, inquilinoB);
        data.activeContractIn(agenciaB, propiedadB, ownerB, inquilinoB);
        ticketB = data.ticketIn(agenciaB, propiedadB, inquilinoB, "ABIERTA");
    }

    @AfterEach
    void limpiarContexto() {
        AgencyContext.clear();
    }

    @Test
    void laAppCorreConRlsActivo() {
        assertThat(rlsConfigCheck.rlsActive()).isTrue();
    }

    @Test
    void cadaAdminSoloVeLosTicketsDeSuInmobiliaria() throws Exception {
        mvc.perform(get("/api/tickets").with(as(adminA, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + ticketA + "')]").exists())
                .andExpect(jsonPath("$[?(@.id == '" + ticketB + "')]").doesNotExist());
        mvc.perform(get("/api/tickets").with(as(adminB, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + ticketB + "')]").exists())
                .andExpect(jsonPath("$[?(@.id == '" + ticketA + "')]").doesNotExist());
    }

    @Test
    void accederPorIdAUnRecursoDeOtraInmobiliariaDa404() throws Exception {
        mvc.perform(get("/api/tickets/" + ticketB).with(as(adminA, "ADMIN"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/properties/" + propiedadB).with(as(adminA, "ADMIN"))).andExpect(status().isNotFound());
    }

    @Test
    void noSePuedeOperarSobreDatosDeOtraInmobiliaria() throws Exception {
        // Asignar a un ticket de B un proveedor de B, actuando como admin de A
        mvc.perform(patch("/api/tickets/" + ticketB + "/assign").param("providerId", proveedorB.toString())
                        .with(as(adminA, "ADMIN")))
                .andExpect(status().isNotFound());
        // Crear un ticket en una propiedad de B desde A
        String body = "{\"title\":\"x\",\"description\":\"y\",\"category\":\"GAS\",\"propertyId\":\"" + propiedadB
                + "\",\"tenantId\":\"" + inquilinoB + "\"}";
        mvc.perform(post("/api/tickets").with(as(adminA, "ADMIN")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void loQueCreaUnaInmobiliariaQuedaEnSuInmobiliaria() throws Exception {
        String body = "{\"title\":\"Luz\",\"description\":\"Se corta\",\"category\":\"ELECTRICIDAD\",\"propertyId\":\"" + propiedadB + "\"}";
        String r = mvc.perform(post("/api/tickets").with(as(adminB, "ADMIN")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID creado = UUID.fromString(r.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1"));
        UUID agencia = data.db().queryForObject("SELECT agency_id FROM propero_test.tickets WHERE id = ?", UUID.class, creado);
        assertThat(agencia).isEqualTo(agenciaB);
    }

    @Test
    void sinAgenciaFijadaNoSeVeNingunaFila() {
        Integer filas = new TransactionTemplate(txManager).execute(s ->
                appJdbc.queryForObject("SELECT count(*) FROM propero_test.tickets", Integer.class));
        assertThat(filas).isZero();
    }

    @Test
    void elSetLocalNoSeFiltraALaSiguienteTransaccion() {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        Integer conAgencia = AgencyContext.callAs(agenciaB, () -> tx.execute(s ->
                appJdbc.queryForObject("SELECT count(*) FROM propero_test.tickets", Integer.class)));
        assertThat(conAgencia).isEqualTo(1);
        // Misma conexion del pool reciclada, sin agencia: no tiene que ver nada
        for (int i = 0; i < 5; i++) {
            Integer despues = tx.execute(s -> appJdbc.queryForObject("SELECT count(*) FROM propero_test.tickets", Integer.class));
            assertThat(despues).isZero();
        }
    }
}
