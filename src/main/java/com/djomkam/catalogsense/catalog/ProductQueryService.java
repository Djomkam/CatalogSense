package com.djomkam.catalogsense.catalog;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Read side: maps entities to views inside a transaction so lazy variants load safely. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ProductQueryService {

    private final ProductRepository productRepository;

    public Page<ProductView> listProducts(String catalogId, int page, int pageSize) {
        return productRepository.findByCatalogId(catalogId, PageRequest.of(page, pageSize, Sort.by("sku")))
                .map(ProductView::from);
    }

    public Optional<ProductView> findProduct(String catalogId, String sku) {
        return productRepository.findByCatalogIdAndSku(catalogId, sku).map(ProductView::from);
    }
}
