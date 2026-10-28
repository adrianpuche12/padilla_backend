package com.padilla.backend.notification;

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
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** PRO-060 / PRO-061 / PRO-062: notificaciones por evento de ticket, bandeja y preferencias. */
@AutoConfigureMockMvc
class NotificationIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private TestData data;

    private int count(UUID user, String tipo) {
        Integer n = data.db().queryForObject("SELECT count(*) FROM propero_test.notification_log WHERE user_id = ? AND tipo = ?",
                Integer.class, user, tipo);
        return n == null ? 0 : n;
    }

    @Test
    void ciclodeUnTicketNotificaACadaActor() throws Exception {
        UUID admin = data.user("ADMIN");
        UUID owner = data.user("OWNER");
        UUID tenant = data.user("TENANT");
        UUID provider = data.user("PROVIDER");
        UUID prop = data.property(owner, tenant);
        data.activeContract(prop, owner, tenant);

        String r = mvc.perform(post("/api/tickets").with(as(tenant, "TENANT")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Humedad\",\"description\":\"Mancha en el techo\",\"category\":\"ALBANILERIA\",\"propertyId\":\"" + prop + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID ticket = UUID.fromString(r.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1"));

        assertThat(count(admin, "TICKET_CREADO")).isEqualTo(1);
        assertThat(count(owner, "TICKET_CREADO")).isEqualTo(1);
        assertThat(count(tenant, "TICKET_CREADO")).isEqualTo(1);

        mvc.perform(patch("/api/tickets/" + ticket + "/assign").param("providerId", provider.toString()).with(as(admin, "ADMIN")))
                .andExpect(status().isOk());
        assertThat(count(provider, "TICKET_ASIGNADO")).isEqualTo(1);
        assertThat(count(tenant, "TICKET_EN_PROGRESO")).isEqualTo(1);

        mvc.perform(patch("/api/tickets/" + ticket + "/status").with(as(provider, "PROVIDER")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"RESUELTA\"}")).andExpect(status().isOk());
        assertThat(count(owner, "TICKET_COMPLETADO")).isEqualTo(1);
        assertThat(count(admin, "TICKET_COMPLETADO")).isEqualTo(1);

        // El texto es institucional: menciona a "la inmobiliaria", no al empleado
        String mensaje = data.db().queryForObject("SELECT mensaje FROM propero_test.notification_log WHERE user_id = ? AND tipo = 'TICKET_ASIGNADO'",
                String.class, tenant);
        assertThat(mensaje).contains("La inmobiliaria");
    }

    @Test
    void bandejaContadorYMarcarLeidas() throws Exception {
        UUID owner = data.user("OWNER");
        UUID tenant = data.user("TENANT");
        UUID prop = data.property(owner, tenant);
        data.activeContract(prop, owner, tenant);
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/tickets").with(as(tenant, "TENANT")).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"T" + i + "\",\"description\":\"d\",\"category\":\"OTRO\",\"propertyId\":\"" + prop + "\"}"));
        }
        mvc.perform(get("/api/notifications/unread-count").with(as(owner, "OWNER"))).andExpect(jsonPath("$.unread").value(3));
        String lista = mvc.perform(get("/api/notifications").param("size", "2").with(as(owner, "OWNER")))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].titulo").value(containsString("propiedad")))
                .andReturn().getResponse().getContentAsString();
        String id = lista.replaceAll("(?s).*?\"id\":\"([^\"]+)\".*", "$1");
        mvc.perform(patch("/api/notifications/" + id + "/read").with(as(owner, "OWNER"))).andExpect(jsonPath("$.updated").value(1));
        mvc.perform(get("/api/notifications/unread-count").with(as(owner, "OWNER"))).andExpect(jsonPath("$.unread").value(2));
        // Nadie marca como leidas notificaciones ajenas
        mvc.perform(patch("/api/notifications/read-all").with(as(tenant, "TENANT")));
        mvc.perform(get("/api/notifications/unread-count").with(as(owner, "OWNER"))).andExpect(jsonPath("$.unread").value(2));
        mvc.perform(patch("/api/notifications/read-all").with(as(owner, "OWNER"))).andExpect(jsonPath("$.updated").value(2));
    }

    @Test
    void preferencias() throws Exception {
        UUID admin = data.user("ADMIN");
        mvc.perform(get("/api/notifications/preferences").with(as(admin, "ADMIN")))
                .andExpect(jsonPath("$.emailEnabled").value(true)).andExpect(jsonPath("$.resumen").value("OFF"));
        mvc.perform(put("/api/notifications/preferences").with(as(admin, "ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emailEnabled\":false,\"resumen\":\"diario\"}"))
                .andExpect(jsonPath("$.emailEnabled").value(false)).andExpect(jsonPath("$.resumen").value("DIARIO"));
        mvc.perform(put("/api/notifications/preferences").with(as(admin, "ADMIN")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"resumen\":\"cada hora\"}")).andExpect(status().isBadRequest());
    }
}
