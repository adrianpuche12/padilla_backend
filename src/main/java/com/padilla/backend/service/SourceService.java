package com.padilla.backend.service;

import com.padilla.backend.entity.legacy.Source;
import com.padilla.backend.repository.legacy.SourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
public class SourceService {

    private static final Logger log = LoggerFactory.getLogger(SourceService.class);

    private final SourceRepository sourceRepository;

    public SourceService(SourceRepository sourceRepository) {
        this.sourceRepository = sourceRepository;
    }

    public List<Source> findAll() {
        try {
            return sourceRepository.findAllByOrderByNameAsc();
        } catch (Exception e) {
            log.warn("[SourceService] Legacy DB no disponible: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public Optional<Source> findById(Integer id) {
        try {
            return sourceRepository.findById(id);
        } catch (Exception e) {
            log.warn("[SourceService] Legacy DB no disponible (findById): {}", e.getMessage());
            return Optional.empty();
        }
    }

    public long count() {
        try {
            return sourceRepository.count();
        } catch (Exception e) {
            log.warn("[SourceService] Legacy DB no disponible (count): {}", e.getMessage());
            return 0L;
        }
    }
}
