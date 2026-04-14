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
            Map.entry("redis", List.of("redis缓存", "redis cache", "缓存")),
            Map.entry("mysql", List.of("my sql", "数据库", "db")),
            Map.entry("mybatis-plus", List.of("mybatis plus", "mp", "分页插件")),
            Map.entry("vue3", List.of("vue 3", "composition api", "script setup")),
            Map.entry("docker", List.of("docker compose", "容器", "compose")),
            Map.entry("jwt", List.of("token", "鉴权", "登录态")),
            Map.entry("序列化", List.of("serialization", "json", "serializer")),
            Map.entry("缓存", List.of("cache", "缓存击穿", "缓存穿透", "缓存雪崩")),
            Map.entry("分页", List.of("page", "pagination", "limit"))
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
