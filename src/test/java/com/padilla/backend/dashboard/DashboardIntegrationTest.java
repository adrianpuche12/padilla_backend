package com.padilla.backend.dashboard;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** PRO-070 / PRO-071: Widget Registry, dashboard por rol y encuestas. */
@AutoConfigureMockMvc
class DashboardIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private TestData data;

    @Test
    void elCatalogoSeSincronizaAlArrancar() {
        Integer activos = data.db().queryForObject("SELECT count(*) FROM propero_test.dashboard_widgets WHERE is_system_active", Integer.class);
        assertThat(activos).isGreaterThanOrEqualTo(16);
    }

    @Test
    void cadaRolVeSusWidgetsYNoLosAjenos() throws Exception {
        mvc.perform(get("/api/dashboard/widgets/available").with(as(data.user("ADMIN"), "ADMIN")))
                .andExpect(jsonPath("$[?(@.code == 'TICKETS_OPEN')].isEnabled").value(true))
                .andExpect(jsonPath("$[?(@.code == 'TICKETS_SLA')]").doesNotExist())
                .andExpect(jsonPath("$[?(@.code == 'MY_TICKETS')]").doesNotExist());
        mvc.perform(get("/api/dashboard/widgets/available").with(as(data.user("TENANT"), "TENANT")))
                .andExpect(jsonPath("$[?(@.code == 'MY_CONTRACT')]").exists())
                .andExpect(jsonPath("$[?(@.code == 'TICKETS_OPEN')]").doesNotExist());
    }

    @Test
    void preferenciasYDatosEnParalelo() throws Exception {
        UUID manager = data.user("MANAGER");
        UUID owner = data.user("OWNER");
        UUID tenant = data.user("TENANT");
        data.ticket(data.property(owner, tenant), tenant, "ABIERTA");

        mvc.perform(put("/api/dashboard/widgets/preferences").with(as(manager, "MANAGER")).contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"widgetCode\":\"TICKETS_OPEN\",\"isEnabled\":true,\"position\":1},"
                                + "{\"widgetCode\":\"PROPERTIES_STATUS\",\"isEnabled\":true,\"position\":2},"
                                + "{\"widgetCode\":\"TICKETS_SLA\",\"isEnabled\":false,\"position\":3}]"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/dashboard/data").with(as(manager, "MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.TICKETS_OPEN.abiertos").exists())
                .andExpect(jsonPath("$.PROPERTIES_STATUS").isArray())
                .andExpect(jsonPath("$.TICKETS_SLA").doesNotExist());
        // un widget no permitido para el rol se rechaza
        mvc.perform(put("/api/dashboard/widgets/preferences").with(as(tenant, "TENANT")).contentType(MediaType.APPLICATION_JSON)
                .content("[{\"widgetCode\":\"TICKETS_OPEN\",\"isEnabled\":true,\"position\":1}]")).andExpect(status().isBadRequest());
        // el inquilino ve sus propios datos
        mvc.perform(get("/api/dashboard/data").with(as(tenant, "TENANT")))
                .andExpect(jsonPath("$.MY_TICKETS[0].estado").value("ABIERTA"));
    }

    @Test
    void encuestaAlCerrarTicketYRatingDelProveedor() throws Exception {
        UUID admin = data.user("ADMIN");
        UUID manager = data.user("MANAGER");
        UUID owner = data.user("OWNER");
        UUID tenant = data.user("TENANT");
        UUID provider = data.user("PROVIDER");
        UUID ticket = data.ticket(data.property(owner, tenant), tenant, "RESUELTA");
        data.db().update("UPDATE propero_test.tickets SET provider_id = ? WHERE id = ?", provider, ticket);

        mvc.perform(patch("/api/tickets/" + ticket + "/status").with(as(admin, "ADMIN")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"CERRADA\"}")).andExpect(status().isOk());

        String pendientes = mvc.perform(get("/api/surveys/pending").with(as(tenant, "TENANT")))
                .andExpect(jsonPath("$.length()").value(1)).andReturn().getResponse().getContentAsString();
        String id = pendientes.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
        mvc.perform(get("/api/surveys/pending").with(as(owner, "OWNER"))).andExpect(jsonPath("$.length()").value(1));

        mvc.perform(post("/api/surveys/" + id + "/answer").with(as(tenant, "TENANT")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"puntuacion\":7}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/surveys/" + id + "/answer").with(as(owner, "OWNER")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"puntuacion\":5}")).andExpect(status().isNotFound());
        mvc.perform(post("/api/surveys/" + id + "/answer").with(as(tenant, "TENANT")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"puntuacion\":2,\"comentario\":\"Tardaron mucho\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/surveys/" + id + "/answer").with(as(tenant, "TENANT")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"puntuacion\":5}")).andExpect(status().isConflict());

        assertThat(data.db().queryForObject("SELECT count(*) FROM propero_test.notification_log WHERE user_id = ? AND tipo = 'FEEDBACK_NEGATIVO'",
                Integer.class, manager)).isEqualTo(1);
        mvc.perform(get("/api/providers/" + provider + "/rating").with(as(provider, "PROVIDER")))
                .andExpect(jsonPath("$.promedio").value(2.0)).andExpect(jsonPath("$.evaluaciones").value(1));
        mvc.perform(get("/api/providers/" + provider + "/rating").with(as(data.user("PROVIDER"), "PROVIDER"))).andExpect(status().isForbidden());
    }
}
