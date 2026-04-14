package com.coding.assistant.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 本地 TF-IDF 向量化服务，无需外部 API 或模型文件。
 * 词表在运行时从文档语料动态构建，首次启动为空时使用内置编程领域词表兜底。
 */
@Slf4j
@Service
public class EmbeddingService {

    // 运行时词表：词 -> 索引
    private final Map<String, Integer> vocab = new ConcurrentHashMap<>();
    // 文档频率：词 -> 出现该词的文档数
    private final Map<String, Integer> docFreq = new ConcurrentHashMap<>();
    private int totalDocs = 0;

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[a-zA-Z\\u4e00-\\u9fff]+");

    // 内置编程领域词表（冷启动兜底）
    private static final List<String> BUILTIN_TERMS = List.of(
        "spring", "boot", "redis", "mysql", "java", "python", "vue", "react",
        "docker", "kubernetes", "api", "rest", "http", "json", "xml", "sql",
        "index", "cache", "session", "token", "jwt", "oauth", "security",
        "thread", "async", "sync", "queue", "stack", "list", "map", "set",
        "class", "interface", "abstract", "extends", "implements", "override",
        "mybatis", "hibernate", "jpa", "orm", "transaction", "rollback",
        "nginx", "tomcat", "maven", "gradle", "git", "ci", "cd", "deploy",
        "algorithm", "sort", "search", "tree", "graph", "hash", "binary",
        "exception", "error", "debug", "log", "test", "mock", "assert",
        "缓存", "穿透", "雪崩", "击穿", "索引", "事务", "锁", "并发", "线程",
        "接口", "实现", "继承", "多态", "封装", "注解", "反射", "泛型",
        "分页", "查询", "插入", "更新", "删除", "连接", "聚合", "分组"
    );

    public EmbeddingService() {
        // 初始化内置词表
        for (String term : BUILTIN_TERMS) {
            vocab.putIfAbsent(term, vocab.size());
            docFreq.put(term, 1);
        }
        totalDocs = 1;
    }

    /**
     * 将文本转为 TF-IDF 向量
     */
    public List<Double> embed(String text) {
        List<String> tokens = tokenize(text);
        if (tokens.isEmpty()) return List.of();

        // 统计词频
        Map<String, Long> tf = tokens.stream()
                .collect(Collectors.groupingBy(t -> t, Collectors.counting()));

        // 确保所有词都在词表中
        tf.keySet().forEach(w -> vocab.putIfAbsent(w, vocab.size()));

        int size = vocab.size();
        double[] vector = new double[size];

        for (Map.Entry<String, Long> entry : tf.entrySet()) {
            Integer idx = vocab.get(entry.getKey());
            if (idx != null && idx < size) {
                double tfVal = (double) entry.getValue() / tokens.size();
                int df = docFreq.getOrDefault(entry.getKey(), 1);
                double idf = Math.log((double) (totalDocs + 1) / (df + 1)) + 1.0;
                vector[idx] = tfVal * idf;
            }
        }

        // 转为 List<Double>
        List<Double> result = new ArrayList<>(size);
        for (double v : vector) result.add(v);
        return result;
    }

    /**
     * 新文档入库时更新 IDF 统计（由 DocumentService 调用）
     */
    public void updateCorpus(String text) {
        List<String> tokens = tokenize(text);
        Set<String> uniqueTokens = new HashSet<>(tokens);
        uniqueTokens.forEach(w -> docFreq.merge(w, 1, Integer::sum));
        totalDocs++;
    }

    /**
     * 余弦相似度（自动对齐向量长度）
     */
    public double cosineSimilarity(List<Double> a, List<Double> b) {
        int size = Math.max(a.size(), b.size());
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < size; i++) {
            double ai = i < a.size() ? a.get(i) : 0.0;
            double bi = i < b.size() ? b.get(i) : 0.0;
            dot += ai * bi;
            normA += ai * ai;
            normB += bi * bi;
        }
        return (normA == 0 || normB == 0) ? 0.0 : dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        var matcher = TOKEN_PATTERN.matcher(text.toLowerCase());
        while (matcher.find()) {
            String token = matcher.group();
            if (token.length() >= 2) tokens.add(token);
        }
        return tokens;
    }
}
