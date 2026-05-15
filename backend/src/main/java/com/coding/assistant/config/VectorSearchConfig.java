package com.coding.assistant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "vector-search")
public class VectorSearchConfig {

    // Enable vector retrieval as primary path.
    private boolean enabled = false;

    // Elasticsearch URIs, comma-separated.
    private String uris = "http://localhost:9200";

    // Document-level index name.
    private String indexName = "knowledge_document_vector";

    // Chunk-level index name.
    private String chunkIndexName = "knowledge_chunk_vector";

    // Enable query/retrieval log sync to Elasticsearch.
    private boolean queryLogEnabled = true;

    // Query log index name.
    private String queryLogIndexName = "query_log_index";

    // Retrieval log index name.
    private String retrievalLogIndexName = "retrieval_log_index";

    // Request timeout in milliseconds.
    private int timeoutMs = 3000;

    // kNN candidate size.
    private int numCandidates = 50;
}
