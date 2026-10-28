package com.padilla.backend.report;

import com.padilla.backend.support.AbstractIntegrationTest;
import com.padilla.backend.support.TestData;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.util.UUID;

import static com.padilla.backend.support.TestData.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** PRO-072 / PRO-073: reportes con exportacion y log de auditoria. */
@AutoConfigureMockMvc
class ReportsAuditIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private TestData data;

    @Test
    void reporteDeTicketsEnJsonExcelYPdf() throws Exception {
        UUID admin = data.user("ADMIN");
        UUID owner = data.user("OWNER");
        UUID tenant = data.user("TENANT");
        UUID prop = data.property(owner, tenant);
        UUID t = data.ticket(prop, tenant, "CERRADA");
        data.db().update("UPDATE propero_test.tickets SET costo_final = 30000, closed_at = now() WHERE id = ?", t);

        mvc.perform(get("/api/reports/tickets").param("propertyId", prop.toString()).with(as(admin, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Tickets"))
                .andExpect(jsonPath("$.rows.length()").value(1))
                .andExpect(jsonPath("$.rows[0].costo").value(30000.00));

        byte[] xlsx = mvc.perform(get("/api/reports/tickets").param("format", "xlsx").param("propertyId", prop.toString()).with(as(admin, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(".xlsx")))
                .andReturn().getResponse().getContentAsByteArray();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            assertThat(wb.getSheetAt(0).getRow(0).getCell(0).getStringCellValue()).isEqualTo("Tickets");
        }

        byte[] pdf = mvc.perform(get("/api/reports/costos").param("format", "pdf").with(as(admin, "ADMIN")))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
    }

    @Test
    void permisosDeLosReportes() throws Exception {
        UUID admin = data.user("ADMIN");
        UUID owner = data.user("OWNER");
        mvc.perform(get("/api/reports/eficiencia").with(as(admin, "ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/reports/eficiencia").with(as(data.user("MANAGER"), "MANAGER"))).andExpect(status().isOk());
        mvc.perform(get("/api/reports/tickets").with(as(owner, "OWNER"))).andExpect(status().isForbidden());
        // el propietario ve su informe anual y no el de otro
        mvc.perform(get("/api/reports/propietario-anual").with(as(owner, "OWNER"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value(org.hamcrest.Matchers.startsWith("Informe anual")));
        mvc.perform(get("/api/reports/propietario-anual").param("ownerId", data.user("OWNER").toString()).with(as(owner, "OWNER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/reports/inexistente").with(as(admin, "ADMIN"))).andExpect(status().isBadRequest());
    }

    @Test
    void lasAccionesSensiblesQuedanAuditadas() throws Exception {
        UUID manager = data.user("MANAGER");
        UUID admin = data.user("ADMIN");
        UUID owner = data.user("OWNER");
        UUID tenant = data.user("TENANT");
        UUID ticket = data.ticket(data.property(owner, tenant), tenant, "ABIERTA");

        mvc.perform(patch("/api/tickets/" + ticket + "/reject").param("reason", "No corresponde").with(as(admin, "ADMIN")))
                .andExpect(status().isOk());

        mvc.perform(get("/api/audit").param("entidad", "TICKET").param("entidadId", ticket.toString()).with(as(manager, "MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].accion").value("TICKET_ESTADO"))
                .andExpect(jsonPath("$.content[0].actorId").value(admin.toString()))
                .andExpect(jsonPath("$.content[0].detalle").value(org.hamcrest.Matchers.containsString("No corresponde")));
        mvc.perform(get("/api/audit").with(as(admin, "ADMIN"))).andExpect(status().isForbidden());
    }
}
