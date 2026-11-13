package com.djomkam.catalogsense.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.core5.http.HttpHost;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.net.URISyntaxException;

@Configuration
@EnableConfigurationProperties(SearchProperties.class)
@ConditionalOnProperty(name = "catalogsense.search.enabled", havingValue = "true", matchIfMissing = true)
public class OpenSearchConfig {

    /**
     * Uses the application's ObjectMapper (with java.time support) so documents serialize
     * the same way as the Kafka messages.
     */
    @Bean(destroyMethod = "")
    public OpenSearchClient openSearchClient(SearchProperties properties, ObjectMapper objectMapper)
            throws URISyntaxException {
        HttpHost host = HttpHost.create(new URI(properties.url()));
        var transport = ApacheHttpClient5TransportBuilder.builder(host)
                .setMapper(new JacksonJsonpMapper(objectMapper.copy()))
                .build();
        return new OpenSearchClient(transport);
    }
}
