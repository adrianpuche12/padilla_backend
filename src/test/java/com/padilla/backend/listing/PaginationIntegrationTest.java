package com.padilla.backend.listing;

import com.padilla.backend.support.AbstractIntegrationTest;
import com.padilla.backend.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static com.padilla.backend.support.TestData.as;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** PRO-040 / PRO-051: paginacion, busqueda y filtros server-side en los listados. */
@AutoConfigureMockMvc
class PaginationIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private TestData data;

    @Test
    void clientesPaginadosConBusquedaPorNombreYDocumento() throws Exception {
        UUID admin = data.user("ADMIN");
        UUID buscado = data.user("OWNER");
        data.db().update("UPDATE propero_test.users SET name = 'Zoe Perez Unica' WHERE id = ?", buscado);
        data.db().update("INSERT INTO propero_test.client_profiles (user_id, persona_type, document_number, agency_id) "
                + "VALUES (?, 'FISICA', '99887766', ?)", buscado, TestData.PADILLA);
        for (int i = 0; i < 30; i++) data.user("TENANT");

        mvc.perform(get("/api/clients").param("page", "0").param("size", "10").with(as(admin, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(10))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(greaterThanOrEqualTo(31)));
        mvc.perform(get("/api/clients").param("page", "0").param("q", "perez UNICA").with(as(admin, "ADMIN")))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].documentNumber").value("99887766"));
        mvc.perform(get("/api/clients").param("page", "0").param("q", "99887766").with(as(admin, "ADMIN")))
                .andExpect(jsonPath("$.content[0].id").value(buscado.toString()));
        // sin page: lista completa como antes (compatibilidad con el frontend actual)
        mvc.perform(get("/api/clients").with(as(admin, "ADMIN")))
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void validaParametrosDePaginacion() throws Exception {
        UUID admin = data.user("ADMIN");
        mvc.perform(get("/api/clients").param("page", "0").param("size", "500").with(as(admin, "ADMIN")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/clients").param("page", "0").param("sort", "password,desc").with(as(admin, "ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ticketsFiltradosRespetanLaVisibilidadDelRol() throws Exception {
        UUID admin = data.user("ADMIN");
        UUID owner = data.user("OWNER");
        UUID tenant = data.user("TENANT");
        UUID otroTenant = data.user("TENANT");
        UUID prop = data.property(owner, tenant);
        UUID ticket = data.ticket(prop, tenant, "ABIERTA");
        data.ticket(data.property(owner, otroTenant), otroTenant, "ABIERTA");
        Integer numero = data.db().queryForObject("SELECT ticket_number FROM propero_test.tickets WHERE id = ?", Integer.class, ticket);

        mvc.perform(get("/api/tickets").param("page", "0").param("q", "#" + numero).with(as(admin, "ADMIN")))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(ticket.toString()));
        mvc.perform(get("/api/tickets").param("page", "0").param("propertyId", prop.toString()).param("status", "ABIERTA")
                        .with(as(admin, "ADMIN")))
                .andExpect(jsonPath("$.totalElements").value(1));
        // el inquilino pagina solo lo suyo
        mvc.perform(get("/api/tickets").param("page", "0").with(as(tenant, "TENANT")))
                .andExpect(jsonPath("$.totalElements").value(1));
        // el propietario ve los tickets de sus dos propiedades
        mvc.perform(get("/api/tickets").param("page", "0").with(as(owner, "OWNER")))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void propiedadesYUsuariosDeSistemaPaginados() throws Exception {
        UUID admin = data.user("ADMIN");
        UUID owner = data.user("OWNER");
        UUID p = data.property(owner, null);
        data.db().update("UPDATE propero_test.properties SET address = 'Bv. Oroño 1234', city = 'Rosario' WHERE id = ?", p);

        mvc.perform(get("/api/properties").param("page", "0").param("q", "oroño").with(as(admin, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/properties").param("page", "0").with(as(owner, "OWNER")))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/users").param("scope", "system").param("page", "0").with(as(admin, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].canManage").exists());
    }
}
