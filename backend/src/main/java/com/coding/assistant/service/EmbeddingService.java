package com.coding.assistant.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 本地 TF-IDF 向量服务，输出固定维度向量。
 */
@Slf4j
@Service
public class EmbeddingService {

    public static final int VECTOR_DIM = 512;

    // 词项 -> 包含该词项的文档数量
    private final Map<String, Integer> docFreq = new ConcurrentHashMap<>();
    private int totalDocs = 0;

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[a-zA-Z\\u4e00-\\u9fff]+");

    private static final List<String> BUILTIN_TERMS = List.of(
            "spring", "boot", "redis", "mysql", "java", "python", "vue", "react",
            "docker", "kubernetes", "api", "rest", "http", "json", "xml", "sql",
            "index", "cache", "session", "token", "jwt", "oauth", "security",
            "thread", "async", "sync", "queue", "stack", "list", "map", "set",
            "class", "interface", "abstract", "extends", "implements", "override",
            "mybatis", "hibernate", "jpa", "orm", "transaction", "rollback",
            "nginx", "tomcat", "maven", "gradle", "git", "ci", "cd", "deploy",
            "algorithm", "sort", "search", "tree", "graph", "hash", "binary",
            "exception", "error", "debug", "log", "test", "mock", "assert"
    );

    public EmbeddingService() {
        for (String term : BUILTIN_TERMS) {
            docFreq.put(term, 1);
        }
        totalDocs = 1;
    }

    /**
     * 将文本转换为固定维度的 TF-IDF 向量。
     */
    public List<Double> embed(String text) {
        List<String> tokens = tokenize(text);
        if (tokens.isEmpty()) {
            return List.of();
        }

        Map<String, Long> tf = tokens.stream()
                .collect(Collectors.groupingBy(t -> t, Collectors.counting()));

        double[] vector = new double[VECTOR_DIM];
        for (Map.Entry<String, Long> entry : tf.entrySet()) {
            String term = entry.getKey();
            double tfVal = (double) entry.getValue() / tokens.size();
            int df = docFreq.getOrDefault(term, 1);
            double idf = Math.log((double) (totalDocs + 1) / (df + 1)) + 1.0;
            double weight = tfVal * idf;
            vector[bucketIndex(term)] += weight;
        }

        double norm = 0.0;
        for (double v : vector) {
            norm += v * v;
        }
        norm = Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < vector.length; i++) {
                vector[i] = vector[i] / norm;
            }
        }

        List<Double> result = new ArrayList<>(VECTOR_DIM);
        for (double v : vector) {
            result.add(v);
        }
        return result;
    }

    /**
     * 新增文档后更新语料统计信息。
     */
    public void updateCorpus(String text) {
        List<String> tokens = tokenize(text);
        Set<String> uniqueTokens = new HashSet<>(tokens);
        uniqueTokens.forEach(w -> docFreq.merge(w, 1, Integer::sum));
        totalDocs++;
    }

    /**
     * 计算两个向量的余弦相似度。
     */
    public double cosineSimilarity(List<Double> a, List<Double> b) {
        int size = Math.max(a.size(), b.size());
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < size; i++) {
            double ai = i < a.size() ? a.get(i) : 0.0;
            double bi = i < b.size() ? b.get(i) : 0.0;
            dot += ai * bi;
            normA += ai * ai;
            normB += bi * bi;
        }
        return (normA == 0 || normB == 0) ? 0.0 : dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * 通过哈希把词项映射到固定维度索引。
     */
    private int bucketIndex(String term) {
        int hash = term == null ? 0 : term.hashCode();
        return Math.floorMod(hash, VECTOR_DIM);
    }

    /**
     * 分词：只保留中英文连续片段，且长度至少为 2。
     */
    private List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        var matcher = TOKEN_PATTERN.matcher(text.toLowerCase());
        while (matcher.find()) {
            String token = matcher.group();
            if (token.length() >= 2) {
                tokens.add(token);
            }
        }
        return tokens;
    }
}
