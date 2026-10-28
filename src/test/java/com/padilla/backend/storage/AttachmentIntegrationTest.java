package com.padilla.backend.storage;

import com.padilla.backend.support.AbstractIntegrationTest;
import com.padilla.backend.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static com.padilla.backend.support.TestData.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** PRO-046 / PRO-047 / PRO-050: adjuntos con control de acceso e historial de propiedad. */
@AutoConfigureMockMvc
class AttachmentIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private TestData data;

    private static MockMultipartFile foto(String nombre) {
        return new MockMultipartFile("file", nombre, "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, 1, 2, 3});
    }

    @Test
    void inquilinoSubeFotosASuTicketYSoloVenQuienesVenElTicket() throws Exception {
        UUID owner = data.user("OWNER");
        UUID tenant = data.user("TENANT");
        UUID otro = data.user("TENANT");
        UUID ticket = data.ticket(data.property(owner, tenant), tenant, "ABIERTA");

        String r = mvc.perform(multipart("/api/tickets/" + ticket + "/attachments").file(foto("canilla.jpg")).with(as(tenant, "TENANT")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("canilla.jpg"))
                .andReturn().getResponse().getContentAsString();
        String id = r.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

        mvc.perform(get("/api/tickets/" + ticket + "/attachments").with(as(owner, "OWNER")))
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/attachments/" + id + "/download").with(as(tenant, "TENANT")))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(content().bytes(new byte[]{(byte) 0xFF, (byte) 0xD8, 1, 2, 3}));
        // otro inquilino: ni lista ni descarga ni sube
        mvc.perform(get("/api/tickets/" + ticket + "/attachments").with(as(otro, "TENANT"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/attachments/" + id + "/download").with(as(otro, "TENANT"))).andExpect(status().isNotFound());
        mvc.perform(multipart("/api/tickets/" + ticket + "/attachments").file(foto("x.jpg")).with(as(otro, "TENANT")))
                .andExpect(status().isNotFound());
    }

    @Test
    void validaTipoYBorrado() throws Exception {
        UUID admin = data.user("ADMIN");
        UUID owner = data.user("OWNER");
        UUID prop = data.property(owner, null);
        mvc.perform(multipart("/api/attachments").file(new MockMultipartFile("file", "x.exe", "application/x-msdownload", new byte[]{1}))
                        .param("entityType", "PROPIEDAD").param("entityId", prop.toString()).with(as(admin, "ADMIN")))
                .andExpect(status().isBadRequest());
        String r = mvc.perform(multipart("/api/attachments")
                        .file(new MockMultipartFile("file", "escritura.pdf", "application/pdf", "%PDF-1.4".getBytes()))
                        .param("entityType", "propiedad").param("entityId", prop.toString()).with(as(admin, "ADMIN")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = r.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
        mvc.perform(delete("/api/attachments/" + id).with(as(owner, "OWNER"))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/attachments/" + id).with(as(admin, "ADMIN"))).andExpect(status().isNoContent());
        assertThat(data.db().queryForObject("SELECT count(*) FROM propero_test.attachments WHERE id = ?::uuid", Integer.class, id)).isZero();
    }

    @Test
    void proveedorCargaSusCertificadosConVencimiento() throws Exception {
        UUID provider = data.user("PROVIDER");
        mvc.perform(multipart("/api/attachments").file(new MockMultipartFile("file", "matricula.pdf", "application/pdf", new byte[]{1}))
                        .param("entityType", "PROVEEDOR").param("entityId", provider.toString())
                        .param("descripcion", "Matrícula de gasista").param("vence", "2027-12-31").with(as(provider, "PROVIDER")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.vence").value("2027-12-31"));
        mvc.perform(multipart("/api/attachments").file(foto("x.jpg"))
                        .param("entityType", "PROVEEDOR").param("entityId", data.user("PROVIDER").toString()).with(as(provider, "PROVIDER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void historialDeLaPropiedad() throws Exception {
        UUID admin = data.user("ADMIN");
        UUID owner = data.user("OWNER");
        UUID tenant = data.user("TENANT");
        UUID prop = data.property(owner, tenant);
        data.activeContract(prop, owner, tenant);
        data.ticket(prop, tenant, "CERRADA");
        mvc.perform(get("/api/properties/" + prop + "/history").with(as(admin, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contracts.length()").value(1))
                .andExpect(jsonPath("$.contracts[0].tenantName").exists())
                .andExpect(jsonPath("$.tickets.length()").value(1));
        mvc.perform(get("/api/properties/" + prop + "/history").with(as(data.user("OWNER"), "OWNER"))).andExpect(status().isNotFound());
    }
}
