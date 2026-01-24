package com.padilla.backend.service;

import com.padilla.backend.entity.Seller;
import com.padilla.backend.repository.SellerRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class SellerService {

    private final SellerRepository sellerRepository;

    public SellerService(SellerRepository sellerRepository) {
        this.sellerRepository = sellerRepository;
    }

    public List<Seller> findAll() {
        return sellerRepository.findAllByOrderByFullnameAsc();
    }

    public Optional<Seller> findById(Integer id) {
        return sellerRepository.findById(id);
    }

    public long count() {
        return sellerRepository.count();
    }
}
