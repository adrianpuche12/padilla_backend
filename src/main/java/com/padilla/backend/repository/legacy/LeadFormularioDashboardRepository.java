package com.padilla.backend.repository.legacy;
import com.padilla.backend.entity.legacy.LeadFormularioDashboard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface LeadFormularioDashboardRepository extends JpaRepository<LeadFormularioDashboard, Integer> {

    List<LeadFormularioDashboard> findAllByOrderByDateDesc();

    List<LeadFormularioDashboard> findByDateOrderByCreatedAtDesc(LocalDate date);

    List<LeadFormularioDashboard> findByIdSellerOrderByDateDesc(Integer idSeller);

    @Query("SELECT l FROM LeadFormularioDashboard l WHERE l.date BETWEEN :startDate AND :endDate ORDER BY l.date DESC")
    List<LeadFormularioDashboard> findByDateRange(LocalDate startDate, LocalDate endDate);

    long countByDate(LocalDate date);

    long countByIdSeller(Integer idSeller);
}
