package com.padilla.backend.repository;

import com.padilla.backend.entity.LeadPortalDashboard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface LeadPortalDashboardRepository extends JpaRepository<LeadPortalDashboard, Integer> {

    List<LeadPortalDashboard> findAllByOrderByDateDesc();

    List<LeadPortalDashboard> findByDateOrderByCreatedAtDesc(LocalDate date);

    List<LeadPortalDashboard> findByIdSellerOrderByDateDesc(Integer idSeller);

    List<LeadPortalDashboard> findByIdSourceOrderByDateDesc(Integer idSource);

    @Query("SELECT l FROM LeadPortalDashboard l WHERE l.date BETWEEN :startDate AND :endDate ORDER BY l.date DESC")
    List<LeadPortalDashboard> findByDateRange(LocalDate startDate, LocalDate endDate);

    long countByDate(LocalDate date);

    long countByIdSeller(Integer idSeller);
}
