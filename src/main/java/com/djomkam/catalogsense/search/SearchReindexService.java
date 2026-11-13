package com.djomkam.catalogsense.search;

import com.djomkam.catalogsense.domain.Product;
import com.djomkam.catalogsense.domain.ProductRepository;
import com.djomkam.catalogsense.event.ProductSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rebuilds a catalog's search documents from PostgreSQL, the source of truth. Used for products
 * stored before the indexer existed, after a mapping change, or to repair the index. Safe to run
 * while events keep flowing: versioned writes never replace a newer document.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "catalogsense.search.enabled", havingValue = "true", matchIfMissing = true)
public class SearchReindexService {

    static final int PAGE_SIZE = 500;

    private final ProductRepository productRepository;
    private final ProductIndexer indexer;

    public record ReindexReport(String catalogId, long indexed, long failed) {
    }

    @Transactional(readOnly = true)
    public ReindexReport reindex(String catalogId) {
        long indexed = 0;
        long failed = 0;
        Page<Product> page;
        int pageNumber = 0;
        do {
            page = productRepository.findByCatalogId(catalogId, PageRequest.of(pageNumber++, PAGE_SIZE, Sort.by("id")));
            var docs = page.getContent().stream().map(ProductSnapshot::of).map(ProductDocument::from).toList();
            int pageFailed = indexer.bulkUpsert(docs);
            failed += pageFailed;
            indexed += docs.size() - pageFailed;
        } while (page.hasNext());
        log.info("Reindexed catalog={} indexed={} failed={}", catalogId, indexed, failed);
        return new ReindexReport(catalogId, indexed, failed);
    }
}
