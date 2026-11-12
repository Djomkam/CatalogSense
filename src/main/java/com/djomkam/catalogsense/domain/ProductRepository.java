package com.djomkam.catalogsense.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByCatalogIdAndSku(String catalogId, String sku);

    Page<Product> findByCatalogId(String catalogId, Pageable pageable);

    long countByCatalogId(String catalogId);

    @Query("select p.sku from Product p where p.catalogId = :catalogId")
    List<String> findSkusByCatalogId(String catalogId);

    List<Product> findByCatalogIdAndSkuIn(String catalogId, Collection<String> skus);
}
