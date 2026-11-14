package com.djomkam.catalogsense.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.core5.http.HttpHost;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.net.URISyntaxException;

@Configuration
@EnableConfigurationProperties(SearchProperties.class)
@ConditionalOnSearchEnabled
public class OpenSearchConfig {

    /**
     * The HTTP connection pool to OpenSearch. Spring calls {@code close()} on shutdown, which releases
     * the pool's connections and threads.
     *
     * <p>Uses a copy of the application's ObjectMapper (with java.time support), so documents serialize
     * the same way as the Kafka messages.</p>
     */
    @Bean
    public OpenSearchTransport openSearchTransport(SearchProperties properties, ObjectMapper objectMapper)
            throws URISyntaxException {
        HttpHost host = HttpHost.create(new URI(properties.url()));
        return ApacheHttpClient5TransportBuilder.builder(host)
                .setMapper(new JacksonJsonpMapper(objectMapper.copy()))
                .build();
    }

    /** The client holds no resources of its own; closing the transport is enough. */
    @Bean
    public OpenSearchClient openSearchClient(OpenSearchTransport transport) {
        return new OpenSearchClient(transport);
    }
}
