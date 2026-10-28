package com.padilla.backend.controller;

import com.padilla.backend.entity.legacy.DailyLead;
import com.padilla.backend.entity.legacy.LeadFormularioDashboard;
import com.padilla.backend.entity.legacy.LeadPortalDashboard;
import com.padilla.backend.service.LeadService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/leads")
public class LeadController {

    private final LeadService leadService;

    private final com.padilla.backend.repository.legacy.LeadPortalDashboardRepository portalRepository;
    private final com.padilla.backend.repository.legacy.LeadFormularioDashboardRepository formularioRepository;
    private final org.springframework.jdbc.core.JdbcTemplate legacyJdbc;
    private final String legacySchema;

    public LeadController(LeadService leadService,
                          com.padilla.backend.repository.legacy.LeadPortalDashboardRepository portalRepository,
                          com.padilla.backend.repository.legacy.LeadFormularioDashboardRepository formularioRepository,
                          @org.springframework.beans.factory.annotation.Qualifier("legacyJdbcTemplate") org.springframework.jdbc.core.JdbcTemplate legacyJdbc,
                          @org.springframework.beans.factory.annotation.Value("${legacy.db.schema:padilla}") String legacySchema) {
        this.leadService = leadService;
        this.portalRepository = portalRepository;
        this.formularioRepository = formularioRepository;
        this.legacyJdbc = legacyJdbc;
        this.legacySchema = legacySchema;
    }

    // =====================
    // Portal Leads
    // =====================

    /** PRO-080: con ?page= devuelve una pagina (sin datos internos de n8n); sin page, la lista como antes. */
    @GetMapping("/portal")
    public ResponseEntity<?> getAllPortalLeads(@RequestParam(required = false) Integer page,
                                               @RequestParam(required = false) Integer size) {
        if (page != null) {
            var p = portalRepository.findAll(com.padilla.backend.util.Paging.request(page, size, null, java.util.Set.of(),
                    org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "date")));
            return ResponseEntity.ok(com.padilla.backend.dto.PageResponse.of(p, l -> {
                java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("id", l.getId());
                m.put("nombre", l.getNombre());
                m.put("telefono", l.getTelefono());
                m.put("linkProperty", l.getLinkProperty());
                m.put("source", l.getSource());
                m.put("vendedor", l.getVendedor());
                m.put("date", l.getDate());
                return m;
            }));
        }
        return ResponseEntity.ok(leadService.findAllPortalLeads());
    }

    @GetMapping("/portal/date/{date}")
    public ResponseEntity<List<LeadPortalDashboard>> getPortalLeadsByDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(leadService.findPortalLeadsByDate(date));
    }

    @GetMapping("/portal/seller/{sellerId}")
    public ResponseEntity<List<LeadPortalDashboard>> getPortalLeadsBySeller(@PathVariable Integer sellerId) {
        return ResponseEntity.ok(leadService.findPortalLeadsBySeller(sellerId));
    }

    @GetMapping("/portal/source/{sourceId}")
    public ResponseEntity<List<LeadPortalDashboard>> getPortalLeadsBySource(@PathVariable Integer sourceId) {
        return ResponseEntity.ok(leadService.findPortalLeadsBySource(sourceId));
    }

    @GetMapping("/portal/range")
    public ResponseEntity<List<LeadPortalDashboard>> getPortalLeadsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(leadService.findPortalLeadsByDateRange(startDate, endDate));
    }

    // =====================
    // Formulario Leads
    // =====================

    /** PRO-080: con ?page= devuelve una pagina (sin datos internos de n8n); sin page, la lista como antes. */
    @GetMapping(value = "/formulario", params = "page")
    public ResponseEntity<?> getFormularioLeadsPage(@RequestParam Integer page, @RequestParam(required = false) Integer size) {
        var p = formularioRepository.findAll(com.padilla.backend.util.Paging.request(page, size, null, java.util.Set.of(),
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "date")));
        return ResponseEntity.ok(com.padilla.backend.dto.PageResponse.of(p, l -> {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", l.getId());
            m.put("nombre", l.getNombre());
            m.put("email", l.getEmail());
            m.put("telefono", l.getTelefono());
            m.put("motivo", l.getMotivo());
            m.put("descripcion", l.getDescripcion());
            m.put("linkProperty", l.getLinkProperty());
            m.put("source", l.getSource());
            m.put("vendedor", l.getVendedor());
            m.put("date", l.getDate());
            return m;
        }));
    }

    /** PRO-081: encuestas NPS del flujo de leads (si la base legacy tiene la tabla). */
    @GetMapping("/satisfaction")
    public ResponseEntity<java.util.List<java.util.Map<String, Object>>> satisfaction() {
        return ResponseEntity.ok(legacyQuery("SELECT * FROM satisfaction_dashboard ORDER BY 1 DESC LIMIT 500"));
    }

    /** PRO-081: pedidos de tasacion (si la base legacy tiene la tabla). */
    @GetMapping("/tasaciones")
    public ResponseEntity<java.util.List<java.util.Map<String, Object>>> tasaciones() {
        return ResponseEntity.ok(legacyQuery("SELECT * FROM tasaciones_dashboard ORDER BY 1 DESC LIMIT 500"));
    }

    /** Lectura tolerante: si la tabla no existe en la base legacy de esta instalacion, lista vacia. */
    private java.util.List<java.util.Map<String, Object>> legacyQuery(String sql) {
        try {
            return legacyJdbc.queryForList(sql.replace("FROM ", "FROM " + legacySchema + "."));
        } catch (org.springframework.dao.DataAccessException e) {
            return java.util.List.of();
        }
    }

    @GetMapping("/formulario")
    public ResponseEntity<List<LeadFormularioDashboard>> getAllFormularioLeads() {
        return ResponseEntity.ok(leadService.findAllFormularioLeads());
    }

    @GetMapping("/formulario/date/{date}")
    public ResponseEntity<List<LeadFormularioDashboard>> getFormularioLeadsByDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(leadService.findFormularioLeadsByDate(date));
    }

    @GetMapping("/formulario/seller/{sellerId}")
    public ResponseEntity<List<LeadFormularioDashboard>> getFormularioLeadsBySeller(@PathVariable Integer sellerId) {
        return ResponseEntity.ok(leadService.findFormularioLeadsBySeller(sellerId));
    }

    @GetMapping("/formulario/range")
    public ResponseEntity<List<LeadFormularioDashboard>> getFormularioLeadsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(leadService.findFormularioLeadsByDateRange(startDate, endDate));
    }

    // =====================
    // Daily Leads
    // =====================

    @GetMapping("/daily")
    public ResponseEntity<List<DailyLead>> getTodayLeads() {
        return ResponseEntity.ok(leadService.findTodayLeads());
    }

    @GetMapping("/daily/{date}")
    public ResponseEntity<List<DailyLead>> getDailyLeads(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(leadService.findDailyLeads(date));
    }

    // =====================
    // Statistics
    // =====================

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        return ResponseEntity.ok(leadService.getStatistics());
    }

    @GetMapping("/stats/summary")
    public ResponseEntity<Map<String, Object>> getSummary() {
        Map<String, Object> summary = new HashMap<>();
        summary.put("statistics", leadService.getStatistics());
        summary.put("portalLeadsToday", leadService.findPortalLeadsByDate(LocalDate.now()));
        summary.put("formularioLeadsToday", leadService.findFormularioLeadsByDate(LocalDate.now()));
        return ResponseEntity.ok(summary);
    }
}
