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

    public LeadController(LeadService leadService) {
        this.leadService = leadService;
    }

    // =====================
    // Portal Leads
    // =====================

    @GetMapping("/portal")
    public ResponseEntity<List<LeadPortalDashboard>> getAllPortalLeads() {
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
