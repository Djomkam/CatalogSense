package com.djomkam.catalogsense.search.indexing;

import com.djomkam.catalogsense.search.ConditionalOnSearchEnabled;
import com.djomkam.catalogsense.search.OpenSearchErrors;
import com.djomkam.catalogsense.search.SearchProperties;
import com.djomkam.catalogsense.search.SearchUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Owns the product index's lifecycle: creating it, and swapping it for a rebuilt one.
 *
 * <h2>Alias and physical indexes</h2>
 * The app never uses a physical index name. It reads and writes through the alias
 * ({@code catalogsense-products}), which points at exactly one physical index
 * ({@code catalogsense-products-v1}, {@code -v2}, ...). A reindex builds the next version next to the
 * current one, then moves the alias and deletes the old index in one atomic request, so shoppers never
 * see a half-built index.
 */
@Component
@Slf4j
@ConditionalOnSearchEnabled
public class ProductIndexManager {

    private final OpenSearchClient client;
    private final String alias;
    private final ProductIndexDefinition definition;
    private final Pattern versionedName;

    /** Set once the alias is known to exist; checked before every write (see {@link #requireIndex()}). */
    private volatile boolean indexReady;

    public ProductIndexManager(OpenSearchClient client, SearchProperties properties) {
        this.client = client;
        this.alias = properties.indexAlias();
        this.definition = ProductIndexDefinition.load(client._transport().jsonpMapper());
        this.versionedName = Pattern.compile(Pattern.quote(alias) + "-v(\\d+)");
    }

    public String alias() {
        return alias;
    }

    /**
     * Creates the first index at startup. If OpenSearch is down, the app still starts (ingestion keeps
     * working); the first write retries the creation through {@link #requireIndex()}.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void createIndexAtStartup() {
        try {
            requireIndex();
        } catch (SearchUnavailableException e) {
            log.warn("Could not create the search index at startup: {}", e.getMessage());
        }
    }

    /**
     * Makes sure the alias exists before a write. Without this, writing to a missing alias would make
     * OpenSearch auto-create a plain index with a guessed mapping. After the first success it's a
     * volatile read.
     */
    public void requireIndex() {
        if (!indexReady) {
            createFirstIndexIfMissing();
            indexReady = true;
        }
    }

    private void createFirstIndexIfMissing() {
        try {
            if (aliasExists()) {
                return;
            }
            if (client.indices().exists(request -> request.index(alias)).value()) {
                throw new SearchUnavailableException("'" + alias + "' is a plain index, not an alias (left by an "
                        + "earlier version of the app). Delete it once: curl -X DELETE <opensearch>/" + alias);
            }
            createIndex(versionName(1), true);
        } catch (OpenSearchException | IOException e) {
            // Another instance may have created it between our check and our create: fine if the alias is there.
            if (!(OpenSearchErrors.isAlreadyExists(e) && aliasExistsQuietly())) {
                throw new SearchUnavailableException("Could not create the search index", e);
            }
        }
    }

    /** Creates the next physical index version (not yet behind the alias) and returns its name. */
    public String createNextIndex() {
        String nextIndex = versionName(currentIndices().stream()
                .mapToInt(this::versionOf)
                .max()
                .orElse(0) + 1);
        try {
            createIndex(nextIndex, false);
            return nextIndex;
        } catch (OpenSearchException | IOException e) {
            throw new SearchUnavailableException("Could not create index " + nextIndex, e);
        }
    }

    /** Atomically points the alias at {@code newIndex} and deletes the index (or indexes) it pointed at. */
    public void switchAliasTo(String newIndex) {
        Set<String> previousIndices = currentIndices();
        try {
            client.indices().updateAliases(request -> {
                request.actions(action -> action.add(add -> add.index(newIndex).alias(alias)));
                previousIndices.forEach(previous ->
                        request.actions(action -> action.removeIndex(remove -> remove.index(previous))));
                return request;
            });
            log.info("Search alias {} now points at {} (removed {})", alias, newIndex, previousIndices);
        } catch (OpenSearchException | IOException e) {
            throw new SearchUnavailableException("Could not move alias " + alias + " to " + newIndex, e);
        }
    }

    /** Best-effort cleanup of an index that will not be used (a failed rebuild). */
    public void deleteIndexQuietly(String index) {
        try {
            client.indices().delete(request -> request.index(index));
        } catch (OpenSearchException | IOException e) {
            log.warn("Could not delete unused index {}: {}", index, e.toString());
        }
    }

    /** Physical indexes the alias currently points at (normally exactly one). */
    Set<String> currentIndices() {
        try {
            return client.indices().getAlias(request -> request.name(alias)).result().keySet();
        } catch (OpenSearchException | IOException e) {
            throw new SearchUnavailableException("Could not read alias " + alias, e);
        }
    }

    private void createIndex(String index, boolean behindAlias) throws IOException {
        client.indices().create(request -> {
            request.index(index).settings(definition.settings()).mappings(definition.mappings());
            if (behindAlias) {
                request.aliases(alias, aliasSettings -> aliasSettings);
            }
            return request;
        });
        log.info("Created OpenSearch index {}{}", index, behindAlias ? " behind alias " + alias : "");
    }

    private boolean aliasExists() throws IOException {
        return client.indices().existsAlias(request -> request.name(alias)).value();
    }

    private boolean aliasExistsQuietly() {
        try {
            return aliasExists();
        } catch (OpenSearchException | IOException e) {
            return false;
        }
    }

    private String versionName(int version) {
        return alias + "-v" + version;
    }

    private int versionOf(String index) {
        Matcher matcher = versionedName.matcher(index);
        return matcher.matches() ? Integer.parseInt(matcher.group(1)) : 0;
    }
}
