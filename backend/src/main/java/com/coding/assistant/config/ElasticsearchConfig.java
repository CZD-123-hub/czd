package com.coding.assistant.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.http.HttpHost;
import org.apache.http.client.config.RequestConfig;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

@Configuration
@RequiredArgsConstructor
public class ElasticsearchConfig {

    private final VectorSearchConfig vectorSearchConfig;
    private final ObjectMapper objectMapper;

    @Bean(destroyMethod = "close")
    public RestClient elasticRestClient() {
        String[] uris = Arrays.stream(vectorSearchConfig.getUris().split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toArray(String[]::new);

        RestClientBuilder builder = RestClient.builder(
                Arrays.stream(uris)
                        .map(HttpHost::create)
                        .toArray(HttpHost[]::new)
        );

        builder.setRequestConfigCallback(requestConfigBuilder ->
                requestConfigBuilder
                        .setConnectTimeout(vectorSearchConfig.getTimeoutMs())
                        .setSocketTimeout(vectorSearchConfig.getTimeoutMs())
        );

        return builder.build();
    }

    @Bean(destroyMethod = "close")
    public ElasticsearchTransport elasticsearchTransport(RestClient restClient) {
        return new RestClientTransport(restClient, new JacksonJsonpMapper(objectMapper));
    }

    @Bean
    public ElasticsearchClient elasticsearchClient(ElasticsearchTransport transport) {
        return new ElasticsearchClient(transport);
    }
}
