package com.freshchain.inventory.repository;

import com.freshchain.inventory.domain.Product;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends JpaRepository<Product, String> {

    List<Product> findBySkuIn(Collection<String> skus);
}
