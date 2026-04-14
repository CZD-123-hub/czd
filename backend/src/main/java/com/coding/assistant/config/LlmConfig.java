package com.coding.assistant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "llm")
public class LlmConfig {

    private String provider;
    private ProviderConfig openai = new ProviderConfig();
    private ProviderConfig chatglm = new ProviderConfig();
    private ProviderConfig qwen = new ProviderConfig();

    @Data
    public static class ProviderConfig {
        private String apiKey;
        private String baseUrl;
        private String model;
    }
}