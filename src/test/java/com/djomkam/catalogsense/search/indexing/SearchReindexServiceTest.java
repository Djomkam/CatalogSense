package com.djomkam.catalogsense.search.indexing;

import com.djomkam.catalogsense.catalog.Product;
import com.djomkam.catalogsense.catalog.ProductRepository;
import com.djomkam.catalogsense.search.ProductDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.data.domain.Limit;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SearchReindexServiceTest {

    private static final String NEW_INDEX = "products-v2";

    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final ProductIndexer indexer = mock(ProductIndexer.class);
    private final ProductIndexManager indexManager = mock(ProductIndexManager.class);
    private final KafkaListenerEndpointRegistry listenerRegistry = mock(KafkaListenerEndpointRegistry.class);
    private final MessageListenerContainer consumer = mock(MessageListenerContainer.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-14T12:00:00Z"), ZoneOffset.UTC);

    private SearchReindexService runningOnCallerThread() {
        return withExecutor(Runnable::run);
    }

    private SearchReindexService withExecutor(Executor executor) {
        return new SearchReindexService(productRepository, indexer, indexManager, listenerRegistry,
                mock(PlatformTransactionManager.class), clock, executor);
    }

    @BeforeEach
    void setUp() {
        when(listenerRegistry.getListenerContainer(ProductEventConsumer.LISTENER_ID)).thenReturn(consumer);
        when(consumer.isRunning()).thenReturn(true);
        when(consumer.isContainerPaused()).thenReturn(true);
        when(indexManager.createNextIndex()).thenReturn(NEW_INDEX);
    }

    @Test
    void copiesEveryProductPageByPageThenSwitchesTheAlias() {
        List<Product> firstPage = products(1, SearchReindexService.PAGE_SIZE);
        List<Product> lastPage = products(SearchReindexService.PAGE_SIZE + 1, 1);
        when(productRepository.findByIdGreaterThanOrderByIdAsc(0L, Limit.of(SearchReindexService.PAGE_SIZE)))
                .thenReturn(firstPage);
        when(productRepository.findByIdGreaterThanOrderByIdAsc(SearchReindexService.PAGE_SIZE,
                Limit.of(SearchReindexService.PAGE_SIZE))).thenReturn(lastPage);

        runningOnCallerThread().start();

        InOrder steps = inOrder(consumer, indexManager, indexer);
        steps.verify(consumer).pause();
        steps.verify(indexManager).createNextIndex();
        steps.verify(indexer, times(2)).bulkIndex(eq(NEW_INDEX), any());
        steps.verify(indexManager).switchAliasTo(NEW_INDEX);
        steps.verify(consumer).resume();
    }

    @Test
    void reportsProgressAndSuccessOnTheJob() {
        List<Product> storedProducts = products(1, 3);
        when(productRepository.findByIdGreaterThanOrderByIdAsc(anyLong(), any())).thenReturn(storedProducts);
        SearchReindexService reindexService = runningOnCallerThread();

        ReindexJob job = reindexService.start();

        assertThat(reindexService.find(job.id())).hasValueSatisfying(finished -> {
            assertThat(finished.status()).isEqualTo(ReindexJob.Status.SUCCEEDED);
            assertThat(finished.targetIndex()).isEqualTo(NEW_INDEX);
            assertThat(finished.indexed()).isEqualTo(3);
            assertThat(finished.finishedAt()).isEqualTo(clock.instant());
        });
    }

    @Test
    void anyRejectedProductKeepsTheOldIndexLive() {
        List<Product> storedProducts = products(1, 3);
        when(productRepository.findByIdGreaterThanOrderByIdAsc(anyLong(), any())).thenReturn(storedProducts);
        when(indexer.bulkIndex(eq(NEW_INDEX), any())).thenReturn(1);
        SearchReindexService reindexService = runningOnCallerThread();

        ReindexJob job = reindexService.start();

        verify(indexManager, never()).switchAliasTo(anyString());
        verify(indexManager).deleteIndexQuietly(NEW_INDEX);
        verify(consumer).resume();
        assertThat(reindexService.find(job.id())).hasValueSatisfying(failed -> {
            assertThat(failed.status()).isEqualTo(ReindexJob.Status.FAILED);
            assertThat(failed.indexed()).isEqualTo(2);
            assertThat(failed.rejected()).isEqualTo(1);
            assertThat(failed.error()).contains("not switched");
        });
    }

    @Test
    void documentsAreBuiltFromTheStoredProducts() {
        List<Product> storedProducts = products(7, 1);
        when(productRepository.findByIdGreaterThanOrderByIdAsc(anyLong(), any())).thenReturn(storedProducts);

        runningOnCallerThread().start();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ProductDocument>> documents = ArgumentCaptor.forClass(List.class);
        verify(indexer).bulkIndex(eq(NEW_INDEX), documents.capture());
        assertThat(documents.getValue()).extracting(ProductDocument::sku).containsExactly("SKU-7");
    }

    @Test
    void onlyOneReindexRunsAtATime() {
        List<Runnable> queuedJobs = new ArrayList<>();
        SearchReindexService reindexService = withExecutor(queuedJobs::add);

        ReindexJob running = reindexService.start();

        assertThatThrownBy(reindexService::start)
                .isInstanceOf(ReindexAlreadyRunningException.class)
                .satisfies(refusal -> assertThat(((ReindexAlreadyRunningException) refusal).runningJob()).isEqualTo(running));
        assertThat(queuedJobs).hasSize(1);
    }

    private static List<Product> products(long firstId, int count) {
        return LongStream.range(firstId, firstId + count).mapToObj(id -> {
            Product product = mock(Product.class);
            when(product.getId()).thenReturn(id);
            when(product.getCatalogId()).thenReturn("demo");
            when(product.getSku()).thenReturn("SKU-" + id);
            return product;
        }).toList();
    }
}
