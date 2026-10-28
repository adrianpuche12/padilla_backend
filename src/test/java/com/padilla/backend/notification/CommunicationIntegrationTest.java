package com.padilla.backend.notification;

import com.padilla.backend.service.notification.ContactInfoGuard;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** PRO-064: mensajeria triangulada sin datos de contacto y comunicados. */
@AutoConfigureMockMvc
class CommunicationIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private TestData data;

    @Test
    void detectaDatosDeContacto() {
        assertThat(ContactInfoGuard.containsContactInfo("llamame al +54 9 341 555-1234")).isTrue();
        assertThat(ContactInfoGuard.containsContactInfo("mi mail es juan.perez@gmail.com")).isTrue();
        assertThat(ContactInfoGuard.containsContactInfo("escribime por WhatsApp")).isTrue();
        assertThat(ContactInfoGuard.containsContactInfo("wa.me/5493415551234")).isTrue();
        assertThat(ContactInfoGuard.containsContactInfo("voy el martes a las 15, son 2 horas")).isFalse();
        assertThat(ContactInfoGuard.containsContactInfo("el presupuesto es de 125000 pesos")).isFalse();
        assertThat(ContactInfoGuard.containsContactInfo("ticket #1234 departamento 3B")).isFalse();
    }

    @Test
    void proveedorNoPuedeDejarSuTelefonoPeroLaInmobiliariaSi() throws Exception {
        UUID admin = data.user("ADMIN");
        UUID owner = data.user("OWNER");
        UUID tenant = data.user("TENANT");
        UUID provider = data.user("PROVIDER");
        UUID ticket = data.ticket(data.property(owner, tenant), tenant, "EN_PROCESO");
        data.db().update("UPDATE propero_test.tickets SET provider_id = ? WHERE id = ?", provider, ticket);

        mvc.perform(post("/api/tickets/" + ticket + "/comments").with(as(provider, "PROVIDER")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Llamame al 341 555 1234 y arreglamos\",\"internal\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("teléfonos")));
        mvc.perform(post("/api/tickets/" + ticket + "/comments").with(as(provider, "PROVIDER")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Paso el martes a las 15\",\"internal\":false}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/tickets/" + ticket + "/comments").with(as(admin, "ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Nota interna: proveedor al 341 555 1234\",\"internal\":true}"))
                .andExpect(status().isCreated());
    }

    @Test
    void comunicadoATodosLosInquilinos() throws Exception {
        UUID admin = data.user("ADMIN");
        UUID t1 = data.user("TENANT");
        UUID owner = data.user("OWNER");
        mvc.perform(post("/api/announcements").with(as(admin, "ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\":\"Corte de agua\",\"mensaje\":\"El jueves hay corte de 9 a 13\",\"roles\":[\"TENANT\"]}"))
                .andExpect(status().isCreated());
        assertThat(data.db().queryForObject("SELECT count(*) FROM propero_test.notification_log WHERE user_id = ? AND tipo = 'COMUNICADO'",
                Integer.class, t1)).isEqualTo(1);
        assertThat(data.db().queryForObject("SELECT count(*) FROM propero_test.notification_log WHERE user_id = ? AND tipo = 'COMUNICADO'",
                Integer.class, owner)).isZero();
        mvc.perform(get("/api/announcements").with(as(admin, "ADMIN"))).andExpect(jsonPath("$[0].titulo").value("Corte de agua"));
        mvc.perform(post("/api/announcements").with(as(t1, "TENANT")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"titulo\":\"x\",\"mensaje\":\"y\",\"roles\":[\"TENANT\"]}")).andExpect(status().isForbidden());
    }
}
