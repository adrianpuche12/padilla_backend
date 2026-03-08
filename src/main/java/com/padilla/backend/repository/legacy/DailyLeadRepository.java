package com.padilla.backend.repository.legacy;
import com.padilla.backend.entity.legacy.DailyLead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface DailyLeadRepository extends JpaRepository<DailyLead, Long> {

    List<DailyLead> findByDateOrderByNameSellerAsc(LocalDate date);

    List<DailyLead> findByTipoLeadOrderByDateDesc(String tipoLead);
}
