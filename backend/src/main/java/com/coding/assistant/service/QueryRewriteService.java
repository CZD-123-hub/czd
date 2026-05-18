package com.coding.assistant.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class QueryRewriteService {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[a-zA-Z0-9_+.#@\\-]+|[\\u4e00-\\u9fff]{2,}");

    private static final Map<String, List<String>> SYNONYMS = Map.ofEntries(
            Map.entry("spring boot", List.of("springboot", "spring-boot", "starter")),
            Map.entry("spring mvc", List.of("springmvc", "controller", "rest controller", "接口")),
            Map.entry("spring security", List.of("security", "认证", "授权", "登录", "权限")),
            Map.entry("redis", List.of("redis缓存", "redis cache", "缓存")),
            Map.entry("mysql", List.of("my sql", "数据库", "db")),
            Map.entry("neo4j", List.of("图数据库", "知识图谱", "cypher", "graph database")),
            Map.entry("mybatis", List.of("sql映射", "mapper", "持久层", "orm")),
            Map.entry("mybatis-plus", List.of("mybatis plus", "mp", "分页插件")),
            Map.entry("vue3", List.of("vue 3", "composition api", "script setup")),
            Map.entry("pinia", List.of("状态管理", "store", "前端状态")),
            Map.entry("echarts", List.of("图表", "可视化", "graph", "关系图")),
            Map.entry("docker", List.of("docker compose", "容器", "compose")),
            Map.entry("kubernetes", List.of("k8s", "容器编排", "集群", "pod")),
            Map.entry("jwt", List.of("token", "鉴权", "登录态")),
            Map.entry("rag", List.of("检索增强生成", "retrieval augmented generation", "知识库问答")),
            Map.entry("embedding", List.of("向量", "向量化", "词向量", "语义向量")),
            Map.entry("knn", List.of("近邻搜索", "向量检索", "相似度检索", "nearest neighbors")),
            Map.entry("elasticsearch", List.of("es", "全文检索", "向量索引", "搜索引擎")),
            Map.entry("sse", List.of("流式响应", "server sent events", "text event stream")),
            Map.entry("序列化", List.of("serialization", "json", "serializer")),
            Map.entry("缓存", List.of("cache", "缓存击穿", "缓存穿透", "缓存雪崩")),
            Map.entry("分页", List.of("page", "pagination", "limit")),
            Map.entry("跨域", List.of("cors", "cross origin", "预检请求")),
            Map.entry("异常处理", List.of("exception", "error code", "全局异常", "business exception")),
            Map.entry("学习路径", List.of("path", "learning path", "推荐路径", "路线图"))
    );

    public List<String> expandTerms(String query) {
        Set<String> terms = new LinkedHashSet<>();
        if (query == null || query.isBlank()) {
            return List.of();
        }

        String normalized = query.toLowerCase(Locale.ROOT).trim();
        terms.add(normalized);

        var matcher = TOKEN_PATTERN.matcher(normalized);
        while (matcher.find()) {
            String token = matcher.group().trim();
            if (token.length() >= 2) {
                terms.add(token);
            }
        }

        for (Map.Entry<String, List<String>> entry : SYNONYMS.entrySet()) {
            String canonical = entry.getKey();
            List<String> variants = entry.getValue();
            boolean matched = normalized.contains(canonical);
            if (!matched) {
                for (String variant : variants) {
                    if (normalized.contains(variant)) {
                        matched = true;
                        break;
                    }
                }
            }
            if (matched) {
                terms.add(canonical);
                terms.addAll(variants);
            }
        }

        List<String> expanded = new ArrayList<>(terms);
        expanded.sort((a, b) -> Integer.compare(b.length(), a.length()));
        return expanded;
    }
}
