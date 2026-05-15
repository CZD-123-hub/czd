package com.coding.assistant.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class RetrievalMetricsService {

    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong totalElapsedMs = new AtomicLong(0);

    private final AtomicLong semanticChunkCount = new AtomicLong(0);
    private final AtomicLong semanticDocVectorFallbackCount = new AtomicLong(0);
    private final AtomicLong semanticLocalFallbackCount = new AtomicLong(0);

    private final AtomicLong semanticHitCount = new AtomicLong(0);
    private final AtomicLong keywordHitCount = new AtomicLong(0);
    private final AtomicLong finalHitCount = new AtomicLong(0);

    private volatile Instant updatedAt = Instant.now();

    public void recordSemanticPath(String path, int hitCount, long elapsedMs) {
        switch (path) {
            case "chunk" -> semanticChunkCount.incrementAndGet();
            case "doc_vector_fallback" -> semanticDocVectorFallbackCount.incrementAndGet();
            case "local_tfidf_fallback" -> semanticLocalFallbackCount.incrementAndGet();
            default -> {
            }
        }
        totalElapsedMs.addAndGet(Math.max(0L, elapsedMs));
        finalHitCount.addAndGet(Math.max(0, hitCount));
        updatedAt = Instant.now();
    }

    public void recordSummary(int semanticHits, int keywordHits, int finalHits, long elapsedMs) {
        totalRequests.incrementAndGet();
        semanticHitCount.addAndGet(Math.max(0, semanticHits));
        keywordHitCount.addAndGet(Math.max(0, keywordHits));
        finalHitCount.addAndGet(Math.max(0, finalHits));
        totalElapsedMs.addAndGet(Math.max(0L, elapsedMs));
        updatedAt = Instant.now();
    }

    public Map<String, Object> snapshot() {
        long requests = totalRequests.get();
        long totalMs = totalElapsedMs.get();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalRequests", requests);
        data.put("avgElapsedMs", requests == 0 ? 0.0 : round((double) totalMs / requests));
        data.put("totalElapsedMs", totalMs);

        Map<String, Long> pathBreakdown = new LinkedHashMap<>();
        pathBreakdown.put("chunk", semanticChunkCount.get());
        pathBreakdown.put("docVectorFallback", semanticDocVectorFallbackCount.get());
        pathBreakdown.put("localTfidfFallback", semanticLocalFallbackCount.get());
        data.put("semanticPathBreakdown", pathBreakdown);

        Map<String, Object> hitStats = new LinkedHashMap<>();
        hitStats.put("semanticHits", semanticHitCount.get());
        hitStats.put("keywordHits", keywordHitCount.get());
        hitStats.put("finalHits", finalHitCount.get());
        hitStats.put("avgFinalHitsPerRequest", requests == 0 ? 0.0 : round((double) finalHitCount.get() / requests));
        data.put("hitStats", hitStats);

        Map<String, Object> fallbackRate = new LinkedHashMap<>();
        long semanticTotal = semanticChunkCount.get() + semanticDocVectorFallbackCount.get() + semanticLocalFallbackCount.get();
        double docFallbackRate = semanticTotal == 0 ? 0.0 : round((double) semanticDocVectorFallbackCount.get() / semanticTotal);
        double localFallbackRate = semanticTotal == 0 ? 0.0 : round((double) semanticLocalFallbackCount.get() / semanticTotal);
        fallbackRate.put("docVectorFallbackRate", docFallbackRate);
        fallbackRate.put("localTfidfFallbackRate", localFallbackRate);
        data.put("fallbackRate", fallbackRate);

        data.put("updatedAt", updatedAt.toString());
        return data;
    }

    public void reset() {
        totalRequests.set(0);
        totalElapsedMs.set(0);

        semanticChunkCount.set(0);
        semanticDocVectorFallbackCount.set(0);
        semanticLocalFallbackCount.set(0);

        semanticHitCount.set(0);
        keywordHitCount.set(0);
        finalHitCount.set(0);

        updatedAt = Instant.now();
    }

    private double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
