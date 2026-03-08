package com.padilla.backend.repository.legacy;
import com.padilla.backend.entity.legacy.Seller;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SellerRepository extends JpaRepository<Seller, Integer> {

    List<Seller> findAllByOrderByFullnameAsc();
}
