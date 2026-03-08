package com.padilla.backend.service;

import com.padilla.backend.entity.legacy.DailyLead;
import com.padilla.backend.entity.legacy.LeadFormularioDashboard;
import com.padilla.backend.entity.legacy.LeadPortalDashboard;
import com.padilla.backend.repository.legacy.DailyLeadRepository;
import com.padilla.backend.repository.legacy.LeadFormularioDashboardRepository;
import com.padilla.backend.repository.legacy.LeadPortalDashboardRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class LeadService {

    private final LeadPortalDashboardRepository portalRepository;
    private final LeadFormularioDashboardRepository formularioRepository;
    private final DailyLeadRepository dailyLeadRepository;

    public LeadService(
            LeadPortalDashboardRepository portalRepository,
            LeadFormularioDashboardRepository formularioRepository,
            DailyLeadRepository dailyLeadRepository) {
        this.portalRepository = portalRepository;
        this.formularioRepository = formularioRepository;
        this.dailyLeadRepository = dailyLeadRepository;
    }

    // Portal Leads
    public List<LeadPortalDashboard> findAllPortalLeads() {
        return portalRepository.findAllByOrderByDateDesc();
    }

    public List<LeadPortalDashboard> findPortalLeadsByDate(LocalDate date) {
        return portalRepository.findByDateOrderByCreatedAtDesc(date);
    }

    public List<LeadPortalDashboard> findPortalLeadsBySeller(Integer sellerId) {
        return portalRepository.findByIdSellerOrderByDateDesc(sellerId);
    }

    public List<LeadPortalDashboard> findPortalLeadsBySource(Integer sourceId) {
        return portalRepository.findByIdSourceOrderByDateDesc(sourceId);
    }

    public List<LeadPortalDashboard> findPortalLeadsByDateRange(LocalDate startDate, LocalDate endDate) {
        return portalRepository.findByDateRange(startDate, endDate);
    }

    // Formulario Leads
    public List<LeadFormularioDashboard> findAllFormularioLeads() {
        return formularioRepository.findAllByOrderByDateDesc();
    }

    public List<LeadFormularioDashboard> findFormularioLeadsByDate(LocalDate date) {
        return formularioRepository.findByDateOrderByCreatedAtDesc(date);
    }

    public List<LeadFormularioDashboard> findFormularioLeadsBySeller(Integer sellerId) {
        return formularioRepository.findByIdSellerOrderByDateDesc(sellerId);
    }

    public List<LeadFormularioDashboard> findFormularioLeadsByDateRange(LocalDate startDate, LocalDate endDate) {
        return formularioRepository.findByDateRange(startDate, endDate);
    }

    // Daily Leads
    public List<DailyLead> findDailyLeads(LocalDate date) {
        return dailyLeadRepository.findByDateOrderByNameSellerAsc(date);
    }

    public List<DailyLead> findTodayLeads() {
        return findDailyLeads(LocalDate.now());
    }

    // Statistics
    public Map<String, Object> getStatistics() {
        LocalDate today = LocalDate.now();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalPortalLeads", portalRepository.count());
        stats.put("totalFormularioLeads", formularioRepository.count());
        stats.put("portalLeadsToday", portalRepository.countByDate(today));
        stats.put("formularioLeadsToday", formularioRepository.countByDate(today));

        return stats;
    }

    public Map<String, Object> getSellerStatistics(Integer sellerId) {
        Map<String, Object> stats = new HashMap<>();
        stats.put("portalLeads", portalRepository.countByIdSeller(sellerId));
        stats.put("formularioLeads", formularioRepository.countByIdSeller(sellerId));

        return stats;
    }
}
