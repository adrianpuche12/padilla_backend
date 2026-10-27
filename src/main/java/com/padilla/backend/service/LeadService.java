package com.padilla.backend.service;

import com.padilla.backend.entity.legacy.DailyLead;
import com.padilla.backend.entity.legacy.LeadFormularioDashboard;
import com.padilla.backend.entity.legacy.LeadPortalDashboard;
import com.padilla.backend.repository.legacy.DailyLeadRepository;
import com.padilla.backend.repository.legacy.LeadFormularioDashboardRepository;
import com.padilla.backend.repository.legacy.LeadPortalDashboardRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class LeadService {

    private static final Logger log = LoggerFactory.getLogger(LeadService.class);

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
        try {
            return portalRepository.findAllByOrderByDateDesc();
        } catch (Exception e) {
            log.warn("[LeadService] Legacy DB no disponible (portal leads): {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<LeadPortalDashboard> findPortalLeadsByDate(LocalDate date) {
        try {
            return portalRepository.findByDateOrderByCreatedAtDesc(date);
        } catch (Exception e) {
            log.warn("[LeadService] Legacy DB no disponible (portal leads by date): {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<LeadPortalDashboard> findPortalLeadsBySeller(Integer sellerId) {
        try {
            return portalRepository.findByIdSellerOrderByDateDesc(sellerId);
        } catch (Exception e) {
            log.warn("[LeadService] Legacy DB no disponible (portal leads by seller): {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<LeadPortalDashboard> findPortalLeadsBySource(Integer sourceId) {
        try {
            return portalRepository.findByIdSourceOrderByDateDesc(sourceId);
        } catch (Exception e) {
            log.warn("[LeadService] Legacy DB no disponible (portal leads by source): {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<LeadPortalDashboard> findPortalLeadsByDateRange(LocalDate startDate, LocalDate endDate) {
        try {
            return portalRepository.findByDateRange(startDate, endDate);
        } catch (Exception e) {
            log.warn("[LeadService] Legacy DB no disponible (portal leads by range): {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // Formulario Leads
    public List<LeadFormularioDashboard> findAllFormularioLeads() {
        try {
            return formularioRepository.findAllByOrderByDateDesc();
        } catch (Exception e) {
            log.warn("[LeadService] Legacy DB no disponible (formulario leads): {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<LeadFormularioDashboard> findFormularioLeadsByDate(LocalDate date) {
        try {
            return formularioRepository.findByDateOrderByCreatedAtDesc(date);
        } catch (Exception e) {
            log.warn("[LeadService] Legacy DB no disponible (formulario leads by date): {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<LeadFormularioDashboard> findFormularioLeadsBySeller(Integer sellerId) {
        try {
            return formularioRepository.findByIdSellerOrderByDateDesc(sellerId);
        } catch (Exception e) {
            log.warn("[LeadService] Legacy DB no disponible (formulario leads by seller): {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<LeadFormularioDashboard> findFormularioLeadsByDateRange(LocalDate startDate, LocalDate endDate) {
        try {
            return formularioRepository.findByDateRange(startDate, endDate);
        } catch (Exception e) {
            log.warn("[LeadService] Legacy DB no disponible (formulario leads by range): {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // Daily Leads
    public List<DailyLead> findDailyLeads(LocalDate date) {
        try {
            return dailyLeadRepository.findByDateOrderByNameSellerAsc(date);
        } catch (Exception e) {
            log.warn("[LeadService] Legacy DB no disponible (daily leads): {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<DailyLead> findTodayLeads() {
        return findDailyLeads(LocalDate.now());
    }

    // Statistics
    public Map<String, Object> getStatistics() {
        LocalDate today = LocalDate.now();
        Map<String, Object> stats = new HashMap<>();
        try {
            stats.put("totalPortalLeads", portalRepository.count());
            stats.put("totalFormularioLeads", formularioRepository.count());
            stats.put("portalLeadsToday", portalRepository.countByDate(today));
            stats.put("formularioLeadsToday", formularioRepository.countByDate(today));
        } catch (Exception e) {
            log.warn("[LeadService] Legacy DB no disponible (statistics): {}", e.getMessage());
            stats.put("totalPortalLeads", 0);
            stats.put("totalFormularioLeads", 0);
            stats.put("portalLeadsToday", 0);
            stats.put("formularioLeadsToday", 0);
        }
        return stats;
    }

    public Map<String, Object> getSellerStatistics(Integer sellerId) {
        Map<String, Object> stats = new HashMap<>();
        try {
            stats.put("portalLeads", portalRepository.countByIdSeller(sellerId));
            stats.put("formularioLeads", formularioRepository.countByIdSeller(sellerId));
        } catch (Exception e) {
            log.warn("[LeadService] Legacy DB no disponible (seller statistics): {}", e.getMessage());
            stats.put("portalLeads", 0);
            stats.put("formularioLeads", 0);
        }
        return stats;
    }
}
