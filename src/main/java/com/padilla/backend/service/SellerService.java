package com.padilla.backend.service;
import com.padilla.backend.dto.SellerSummaryDTO;

import com.padilla.backend.entity.legacy.Seller;
import com.padilla.backend.repository.legacy.SellerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
public class SellerService {

    private static final Logger log = LoggerFactory.getLogger(SellerService.class);

    private final SellerRepository sellerRepository;

    public SellerService(SellerRepository sellerRepository) {
        this.sellerRepository = sellerRepository;
    }

    public List<Seller> findAll() {
        try {
            return sellerRepository.findAllByOrderByFullnameAsc();
        } catch (Exception e) {
            log.warn("[SellerService] Legacy DB no disponible: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public Optional<Seller> findById(Integer id) {
        try {
            return sellerRepository.findById(id);
        } catch (Exception e) {
            log.warn("[SellerService] Legacy DB no disponible (findById): {}", e.getMessage());
            return Optional.empty();
        }
    }

    public long count() {
        try {
            return sellerRepository.count();
        } catch (Exception e) {
            log.warn("[SellerService] Legacy DB no disponible (count): {}", e.getMessage());
            return 0L;
        }
    }

    public List<SellerSummaryDTO> findAllSummary() {
        try {
            return sellerRepository.findAllByOrderByFullnameAsc().stream()
                    .map(s -> new SellerSummaryDTO(s.getId(), s.getFullname(), s.getLatestAssignee()))
                    .toList();
        } catch (Exception e) {
            log.warn("[SellerService] Legacy DB no disponible (findAllSummary): {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
