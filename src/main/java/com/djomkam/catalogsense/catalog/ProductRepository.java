package com.djomkam.catalogsense.catalog;

import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByCatalogIdAndSku(String catalogId, String sku);

    Page<Product> findByCatalogId(String catalogId, Pageable pageable);

    long countByCatalogId(String catalogId);

    /** Keyset paging over every catalog: the next {@code limit} products with an id above {@code afterId}. */
    List<Product> findByIdGreaterThanOrderByIdAsc(long afterId, Limit limit);

    @Query("select p.sku from Product p where p.catalogId = :catalogId")
    List<String> findSkusByCatalogId(String catalogId);

    List<Product> findByCatalogIdAndSkuIn(String catalogId, Collection<String> skus);

    /**
     * Bulk delete without loading the entities. The database removes their variants
     * ({@code ON DELETE CASCADE}); pending changes are flushed first so the delete sees them.
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from Product p where p.catalogId = :catalogId and p.sku in :skus")
    int deleteByCatalogIdAndSkuIn(String catalogId, Collection<String> skus);
}
