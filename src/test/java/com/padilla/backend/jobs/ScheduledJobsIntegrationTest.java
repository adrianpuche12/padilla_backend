package com.padilla.backend.jobs;

import com.padilla.backend.service.jobs.ScheduledJobs;
import com.padilla.backend.support.AbstractIntegrationTest;
import com.padilla.backend.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.UUID;

import static com.padilla.backend.support.TestData.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** PRO-044 / PRO-045 / PRO-054 (SLA) / PRO-063: jobs programados y periodos de contrato. */
@AutoConfigureMockMvc
class ScheduledJobsIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private TestData data;
    @Autowired private ScheduledJobs jobs;

    private int notifs(UUID user, String tipo) {
        Integer n = data.db().queryForObject("SELECT count(*) FROM propero_test.notification_log WHERE user_id = ? AND tipo = ?",
                Integer.class, user, tipo);
        return n == null ? 0 : n;
    }

    @Test
    void contratoVencidoPasaAExpiredYLiberaLaPropiedad() {
        UUID owner = data.user("OWNER");
        UUID tenant = data.user("TENANT");
        UUID prop = data.property(owner, tenant);
        UUID contrato = data.activeContract(prop, owner, tenant);
        data.db().update("UPDATE propero_test.contracts SET end_date = ? WHERE id = ?", LocalDate.now().minusDays(1), contrato);

        jobs.contractsExpiration(LocalDate.now());

        assertThat(data.db().queryForObject("SELECT status FROM propero_test.contracts WHERE id = ?", String.class, contrato)).isEqualTo("EXPIRED");
        assertThat(data.db().queryForMap("SELECT tenant_id, status FROM propero_test.properties WHERE id = ?", prop))
                .containsEntry("tenant_id", null).containsEntry("status", "AVAILABLE");
        assertThat(notifs(owner, "CONTRATO_VENCIDO")).isEqualTo(1);
    }

    @Test
    void alertaDeVencimientoUnaSolaVez() {
        UUID admin = data.user("ADMIN");
        UUID owner = data.user("OWNER");
        UUID tenant = data.user("TENANT");
        UUID prop = data.property(owner, tenant);
        UUID contrato = data.activeContract(prop, owner, tenant);
        data.db().update("UPDATE propero_test.contracts SET end_date = ? WHERE id = ?", LocalDate.now().plusDays(20), contrato);

        jobs.contractsExpiration(LocalDate.now());
        jobs.contractsExpiration(LocalDate.now());

        assertThat(notifs(owner, "CONTRATO_POR_VENCER")).isEqualTo(1);
        assertThat(notifs(admin, "CONTRATO_POR_VENCER")).isEqualTo(1);
    }

    @Test
    void slaAvisaASupervisoresPorTicketsViejosSinTomar() {
        UUID manager = data.user("MANAGER");
        UUID owner = data.user("OWNER");
        UUID tenant = data.user("TENANT");
        UUID ticket = data.ticket(data.property(owner, tenant), tenant, "ABIERTA");
        data.db().update("UPDATE propero_test.tickets SET created_at = now() - interval '3 days' WHERE id = ?", ticket);

        jobs.slaAlerts();
        jobs.slaAlerts();

        Integer n = data.db().queryForObject("SELECT count(*) FROM propero_test.notification_log WHERE user_id = ? "
                + "AND tipo = 'SLA_INCUMPLIDO' AND referencia_id = ? AND prioridad = 'URGENTE'", Integer.class, manager, ticket);
        assertThat(n).isEqualTo(1);
    }

    @Test
    void resumenDiarioParaQuienLoPidio() {
        UUID admin = data.user("ADMIN");
        data.db().update("INSERT INTO propero_test.notification_preferences (user_id, resumen, agency_id) VALUES (?, 'DIARIO', ?)",
                admin, TestData.PADILLA);
        jobs.digests(LocalDate.now());
        assertThat(notifs(admin, "RESUMEN_DIARIO")).isEqualTo(1);
    }

    @Test
    void periodosDeIndexacion() throws Exception {
        UUID admin = data.user("ADMIN");
        UUID owner = data.user("OWNER");
        UUID tenant = data.user("TENANT");
        UUID contrato = data.activeContract(data.property(owner, tenant), owner, tenant);
        data.db().update("UPDATE propero_test.contracts SET commission_pct = 5 WHERE id = ?", contrato);
        data.db().update("INSERT INTO propero_test.contract_periods (id, contract_id, period_from, rent_amount, created_at, agency_id) "
                + "VALUES (gen_random_uuid(), ?, current_date - 30, 100000, now(), ?)", contrato, TestData.PADILLA);

        mvc.perform(post("/api/contracts/" + contrato + "/periods").with(as(admin, "ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"periodFrom\":\"" + LocalDate.now() + "\",\"adjustmentIndex\":1.25}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rentAmount").value(125000.00))
                .andExpect(jsonPath("$.commissionAmount").value(6250.00));
        assertThat(data.db().queryForObject("SELECT monthly_amount FROM propero_test.contracts WHERE id = ?", java.math.BigDecimal.class, contrato))
                .isEqualByComparingTo("125000");
        // no se puede cargar un periodo anterior al ultimo
        mvc.perform(post("/api/contracts/" + contrato + "/periods").with(as(admin, "ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"periodFrom\":\"" + LocalDate.now().minusDays(10) + "\",\"rentAmount\":1}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/contracts/" + contrato + "/periods").with(as(tenant, "TENANT")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"periodFrom\":\"" + LocalDate.now().plusDays(30) + "\",\"rentAmount\":1}"))
                .andExpect(status().isForbidden());
    }
}
