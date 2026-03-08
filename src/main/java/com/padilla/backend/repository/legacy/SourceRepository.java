package com.padilla.backend.repository.legacy;
import com.padilla.backend.entity.legacy.Source;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SourceRepository extends JpaRepository<Source, Integer> {

    List<Source> findAllByOrderByNameAsc();
}
