package com.coding.assistant.ai;

import com.coding.assistant.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class LlmProviderFactory {

    @Value("${llm.provider}")
    private String providerName;

    private final Map<String, LlmProvider> providers;

    public LlmProviderFactory(Map<String, LlmProvider> providers) {
        this.providers = providers;
    }

    public LlmProvider getProvider() {
        String beanName = providerName + "Provider";
        LlmProvider provider = providers.get(beanName);
        if (provider == null) {
            log.warn("LLM provider '{}' not found, available providers: {}", providerName, providers.keySet());
            throw new BusinessException(500, "LLM provider '" + providerName + "' is not configured or not available");
        }
        return provider;
    }
}