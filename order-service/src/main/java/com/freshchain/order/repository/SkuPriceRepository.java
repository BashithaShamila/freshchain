package com.freshchain.order.repository;

import com.freshchain.order.domain.SkuPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SkuPriceRepository extends JpaRepository<SkuPrice, String> {
}
