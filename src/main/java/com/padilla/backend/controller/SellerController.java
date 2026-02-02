package com.padilla.backend.controller;

import com.padilla.backend.dto.SellerSummaryDTO;
import com.padilla.backend.entity.Seller;
import com.padilla.backend.service.LeadService;
import com.padilla.backend.service.SellerService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sellers")
public class SellerController {

    private final SellerService sellerService;
    private final LeadService leadService;

    public SellerController(SellerService sellerService, LeadService leadService) {
        this.sellerService = sellerService;
        this.leadService = leadService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<List<SellerSummaryDTO>> getAllSellers() {
        return ResponseEntity.ok(sellerService.findAllSummary());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<Seller> getSellerById(@PathVariable Integer id) {
        return sellerService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/stats")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> getSellerStats(@PathVariable Integer id) {
        return sellerService.findById(id)
                .map(seller -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("seller", seller);
                    response.put("stats", leadService.getSellerStatistics(id));
                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/count")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<Map<String, Long>> getCount() {
        Map<String, Long> response = new HashMap<>();
        response.put("count", sellerService.count());
        return ResponseEntity.ok(response);
    }
}
