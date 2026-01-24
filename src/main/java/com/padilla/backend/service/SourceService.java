package com.padilla.backend.service;

import com.padilla.backend.entity.Source;
import com.padilla.backend.repository.SourceRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class SourceService {

    private final SourceRepository sourceRepository;

    public SourceService(SourceRepository sourceRepository) {
        this.sourceRepository = sourceRepository;
    }

    public List<Source> findAll() {
        return sourceRepository.findAllByOrderByNameAsc();
    }

    public Optional<Source> findById(Integer id) {
        return sourceRepository.findById(id);
    }

    public long count() {
        return sourceRepository.count();
    }
}
