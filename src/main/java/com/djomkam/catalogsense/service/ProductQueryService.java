package com.djomkam.catalogsense.service;

import com.djomkam.catalogsense.api.ProductView;
import com.djomkam.catalogsense.domain.ProductRepository;
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

    public Page<ProductView> list(String catalogId, int page, int size) {
        return productRepository.findByCatalogId(catalogId, PageRequest.of(page, size, Sort.by("sku")))
                .map(ProductView::from);
    }

    public Optional<ProductView> get(String catalogId, String sku) {
        return productRepository.findByCatalogIdAndSku(catalogId, sku).map(ProductView::from);
    }
}
