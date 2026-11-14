package com.djomkam.catalogsense.search.indexing;

import com.djomkam.catalogsense.catalog.Product;
import com.djomkam.catalogsense.catalog.ProductRepository;
import com.djomkam.catalogsense.event.ProductSnapshot;
import com.djomkam.catalogsense.search.ConditionalOnSearchEnabled;
import com.djomkam.catalogsense.search.ProductDocument;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

/**
 * Rebuilds the whole search index from PostgreSQL, the source of truth, without downtime.
 *
 * <h2>Steps</h2>
 * <ol>
 *   <li>Pause the search consumer, so no event writes to the old index while the new one is built.
 *       Events keep queuing in Kafka.</li>
 *   <li>Create the next physical index ({@code ...-v<n+1>}) next to the live one.</li>
 *   <li>Copy every product into it, 500 at a time, paging by id ({@code id > lastId}) with one short
 *       read-only transaction per page. Keyset paging stays fast at any depth, unlike offset paging.</li>
 *   <li>If nothing was rejected, move the alias to the new index and delete the old one, atomically.</li>
 *   <li>Resume the consumer. The queued events are applied to the new index; external versioning
 *       skips the ones the copy already contains.</li>
 * </ol>
 *
 * <p>Why a full rebuild rather than a per-catalog repair: the new index only contains what PostgreSQL
 * holds, so documents of deleted products disappear. And because the consumer is paused, a delete
 * can't be overtaken by the copy: a product deleted during the rebuild has its delete event applied
 * after the switch, so it can't come back. The trade-off is that the cost is the whole index, not one
 * catalog, which is fine for an occasional admin operation.</p>
 *
 * <p>Limitation: pausing only stops the consumer in this app instance. With several instances, the
 * others would keep writing to the old index until the switch; run the reindex with a single indexer
 * instance (or stop the others) until it's done.</p>
 *
 * <p>The job runs on its own thread; the endpoint returns 202 with a job id to poll. Only one runs at
 * a time, and job state is kept in memory (lost on restart, which is safe: the alias only moves at the
 * end, and an unfinished new index is just an orphan to delete).</p>
 */
@Service
@Slf4j
@ConditionalOnSearchEnabled
public class SearchReindexService {

    static final int PAGE_SIZE = 500;
    static final Duration CONSUMER_PAUSE_TIMEOUT = Duration.ofSeconds(30);

    private final ProductRepository productRepository;
    private final ProductIndexer indexer;
    private final ProductIndexManager indexManager;
    private final KafkaListenerEndpointRegistry listenerRegistry;
    private final TransactionTemplate readOnlyTransaction;
    private final Clock clock;
    private final Executor jobExecutor;

    private final AtomicReference<ReindexJob> latestJob = new AtomicReference<>();

    @Autowired
    public SearchReindexService(ProductRepository productRepository, ProductIndexer indexer,
                                ProductIndexManager indexManager, KafkaListenerEndpointRegistry listenerRegistry,
                                PlatformTransactionManager transactionManager, Clock clock) {
        this(productRepository, indexer, indexManager, listenerRegistry, transactionManager, clock,
                Executors.newSingleThreadExecutor(Thread.ofPlatform().name("search-reindex").daemon().factory()));
    }

    /** For tests: run the job on the given executor (e.g. the calling thread). */
    SearchReindexService(ProductRepository productRepository, ProductIndexer indexer,
                         ProductIndexManager indexManager, KafkaListenerEndpointRegistry listenerRegistry,
                         PlatformTransactionManager transactionManager, Clock clock, Executor jobExecutor) {
        this.productRepository = productRepository;
        this.indexer = indexer;
        this.indexManager = indexManager;
        this.listenerRegistry = listenerRegistry;
        this.readOnlyTransaction = new TransactionTemplate(transactionManager);
        this.readOnlyTransaction.setReadOnly(true);
        this.clock = clock;
        this.jobExecutor = jobExecutor;
    }

    /**
     * Starts a rebuild in the background and returns its initial state.
     *
     * @throws ReindexAlreadyRunningException if a rebuild is still running
     */
    public ReindexJob start() {
        ReindexJob newJob = ReindexJob.startedAt(clock.instant());
        ReindexJob previousJob = latestJob.getAndUpdate(current ->
                current != null && current.isRunning() ? current : newJob);
        if (previousJob != null && previousJob.isRunning()) {
            throw new ReindexAlreadyRunningException(previousJob);
        }
        jobExecutor.execute(this::rebuildIndex);
        return newJob;
    }

    public Optional<ReindexJob> find(UUID jobId) {
        return Optional.ofNullable(latestJob.get()).filter(job -> job.id().equals(jobId));
    }

    private void rebuildIndex() {
        MessageListenerContainer consumer = listenerRegistry.getListenerContainer(ProductEventConsumer.LISTENER_ID);
        boolean consumerPaused = false;
        String newIndex = null;
        try {
            consumerPaused = pause(consumer);
            newIndex = indexManager.createNextIndex();
            String targetIndex = newIndex;
            updateJob(job -> job.buildingInto(targetIndex));

            copyAllProductsInto(newIndex);

            ReindexJob progress = latestJob.get();
            if (progress.rejected() > 0) {
                throw new IllegalStateException(progress.rejected() + " product(s) were rejected by OpenSearch; "
                        + "the alias was not switched (see the logs)");
            }
            indexManager.switchAliasTo(newIndex);
            Instant finishedAt = clock.instant();
            updateJob(job -> job.succeededAt(finishedAt));
            log.info("Reindex finished: {} product(s) in {}", progress.indexed(), newIndex);
        } catch (RuntimeException e) {
            log.error("Reindex failed", e);
            if (newIndex != null) {
                indexManager.deleteIndexQuietly(newIndex);
            }
            Instant finishedAt = clock.instant();
            updateJob(job -> job.failedAt(finishedAt, e.getMessage()));
        } finally {
            if (consumerPaused) {
                consumer.resume();
            }
        }
    }

    private void copyAllProductsInto(String index) {
        long lastProductId = 0;
        while (true) {
            long afterId = lastProductId;
            ProductPage page = readOnlyTransaction.execute(status -> loadPageAfter(afterId));
            if (page == null || page.documents().isEmpty()) {
                return;
            }
            int rejected = indexer.bulkIndex(index, page.documents());
            updateJob(job -> job.addProgress(page.documents().size() - rejected, rejected));
            lastProductId = page.lastProductId();
            if (page.documents().size() < PAGE_SIZE) {
                return;
            }
        }
    }

    /** Runs inside the read-only transaction, because building a snapshot reads the variants. */
    private ProductPage loadPageAfter(long afterId) {
        List<Product> products = productRepository.findByIdGreaterThanOrderByIdAsc(afterId, Limit.of(PAGE_SIZE));
        List<ProductDocument> documents = products.stream()
                .map(ProductSnapshot::of)
                .map(ProductDocument::from)
                .toList();
        long lastProductId = products.isEmpty() ? afterId : products.getLast().getId();
        return new ProductPage(documents, lastProductId);
    }

    private record ProductPage(List<ProductDocument> documents, long lastProductId) {
    }

    /** Pauses the consumer and waits until it has really stopped polling. Returns false if it wasn't running. */
    private boolean pause(MessageListenerContainer consumer) {
        if (consumer == null || !consumer.isRunning()) {
            return false;
        }
        consumer.pause();
        Instant deadline = clock.instant().plus(CONSUMER_PAUSE_TIMEOUT);
        while (!consumer.isContainerPaused()) {
            if (clock.instant().isAfter(deadline)) {
                consumer.resume();
                throw new IllegalStateException("The search consumer did not pause within " + CONSUMER_PAUSE_TIMEOUT);
            }
            sleepBriefly();
        }
        return true;
    }

    private static void sleepBriefly() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while pausing the search consumer", e);
        }
    }

    private void updateJob(UnaryOperator<ReindexJob> change) {
        latestJob.updateAndGet(change);
    }

    @PreDestroy
    void stopJobThread() {
        if (jobExecutor instanceof ExecutorService executorService) {
            executorService.shutdownNow();
        }
    }
}
