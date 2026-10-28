package com.padilla.backend.saas;

import com.padilla.backend.support.AbstractIntegrationTest;
import com.padilla.backend.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static com.padilla.backend.support.TestData.as;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** PRO-024 / PRO-025: configuracion por inmobiliaria y limites del plan. */
@AutoConfigureMockMvc
class AgencySettingsIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private TestData data;

    @Test
    void padillaTieneSusDatosLegalesCargados() throws Exception {
        mvc.perform(get("/api/settings/agency").with(as(data.user("ADMIN"), "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agencyName").value("Inmobiliaria Padilla"))
                .andExpect(jsonPath("$.corredorMatricula").value("Mat. C.I.C.I.C.P.S.F. N° 829"))
                .andExpect(jsonPath("$.emailContratos").value("contratos@sramascopadilla.com.ar"));
    }

    @Test
    void soloElSuperAdminEditaYCadaAgenciaVeLoSuyo() throws Exception {
        UUID agencia = data.agency("Inmobiliaria Norte");
        UUID superAdmin = data.userIn(agencia, "SUPER_ADMIN");
        UUID admin = data.userIn(agencia, "ADMIN");
        String body = "{\"razonSocial\":\"NORTE SRL\",\"corredorNombre\":\"Laura Gomez\",\"comisionPct\":4.5,"
                + "\"emailContratos\":\"contratos@norte.test\",\"colorPrimario\":\"#123ABC\"}";

        mvc.perform(put("/api/settings/agency").with(as(admin, "ADMIN")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/settings/agency").with(as(superAdmin, "SUPER_ADMIN")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.razonSocial").value("NORTE SRL"));
        mvc.perform(get("/api/settings/agency").with(as(admin, "ADMIN")))
                .andExpect(jsonPath("$.agencyName").value("Inmobiliaria Norte"))
                .andExpect(jsonPath("$.corredorNombre").value("Laura Gomez"));
        // Padilla no cambio
        mvc.perform(get("/api/settings/agency").with(as(data.user("ADMIN"), "ADMIN")))
                .andExpect(jsonPath("$.razonSocial").value("SEBASTIAN RAMASCO PADILLA, NEGOCIOS INMOBILIARIOS"));
    }

    @Test
    void validaFormato() throws Exception {
        UUID agencia = data.agency("Inmobiliaria Sur");
        UUID superAdmin = data.userIn(agencia, "SUPER_ADMIN");
        mvc.perform(put("/api/settings/agency").with(as(superAdmin, "SUPER_ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"colorPrimario\":\"rojo\",\"emailContratos\":\"no-es-mail\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.colorPrimario").exists())
                .andExpect(jsonPath("$.fields.emailContratos").exists());
    }

    @Test
    void planStarterLimitaLasPropiedades() throws Exception {
        UUID agencia = data.agency("Inmobiliaria Chica");
        UUID admin = data.userIn(agencia, "ADMIN");
        UUID owner = data.userIn(agencia, "OWNER");
        data.db().update("INSERT INTO propero_test.properties (id, active, address, created_at, owner_id, status, type, agency_id) "
                + "SELECT gen_random_uuid(), TRUE, 'Calle ' || g, NOW(), ?, 'AVAILABLE', 'CASA', ? FROM generate_series(1, 150) g",
                owner, agencia);

        mvc.perform(get("/api/settings/plan").with(as(admin, "ADMIN")))
                .andExpect(jsonPath("$.plan").value("STARTER"))
                .andExpect(jsonPath("$.maxProperties").value(150));
        mvc.perform(post("/api/properties").with(as(admin, "ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"address\":\"Una mas 123\",\"type\":\"CASA\",\"ownerId\":\"" + owner + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("150 propiedades")));
    }
}
