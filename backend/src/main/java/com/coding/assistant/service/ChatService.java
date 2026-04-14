package com.coding.assistant.service;

import com.coding.assistant.ai.ChatMessage;
import com.coding.assistant.ai.LlmProviderFactory;
import com.coding.assistant.dto.ConversationVO;
import com.coding.assistant.dto.MessageVO;
import com.coding.assistant.dto.ChatSendRequest;
import com.coding.assistant.entity.Conversation;
import com.coding.assistant.entity.Message;
import com.coding.assistant.exception.BusinessException;
import com.coding.assistant.mapper.ConversationMapper;
import com.coding.assistant.mapper.MessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final LlmProviderFactory llmProviderFactory;
    private final KnowledgeRetrievalService knowledgeRetrievalService;
    private final ChatCacheService chatCacheService;

    private static final String SYSTEM_PROMPT = """
            You are an intelligent coding learning assistant.
            Always answer in Chinese unless the user explicitly requests another language.
            Prioritize retrieved knowledge context over generic knowledge.
            If the retrieved knowledge is insufficient, clearly say which parts are inferred.
            Keep answers accurate, concrete, and focused on programming learning.
            Use the following structure when possible:
            1. 结论
            2. 原理说明
            3. 实现步骤
            4. 示例代码
            5. 常见问题/注意事项
            6. 参考来源
            Never fabricate configuration keys, API names, or framework behavior.""";

    @Transactional
    public Flux<String> sendMessage(Long userId, ChatSendRequest request) {
        Conversation conversation;
        if (request.getConversationId() == null) {
            conversation = Conversation.builder()
                    .userId(userId)
                    .title(generateTitle(request.getContent()))
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            conversationMapper.insert(conversation);
        } else {
            conversation = conversationMapper.selectById(request.getConversationId());
            if (conversation == null || !conversation.getUserId().equals(userId)) {
                throw new BusinessException(404, "Conversation not found");
            }
            conversation.setUpdatedAt(LocalDateTime.now());
            conversationMapper.updateById(conversation);
        }

        Message userMessage = Message.builder()
                .conversationId(conversation.getId())
                .role("user")
                .content(request.getContent())
                .createdAt(LocalDateTime.now())
                .build();
        messageMapper.insert(userMessage);

        Long conversationId = conversation.getId();
        String questionHash = createQuestionHash(request.getContent());

        Optional<ChatCacheService.QaCacheEntry> cachedAnswer = chatCacheService.getCachedAnswer(questionHash);
        if (cachedAnswer.isPresent()) {
            ChatCacheService.QaCacheEntry cacheEntry = cachedAnswer.get();
            Message assistantMessage = Message.builder()
                    .conversationId(conversationId)
                    .role("assistant")
                    .content(cacheEntry.getAnswer())
                    .sources(cacheEntry.getSourcesJson())
                    .createdAt(LocalDateTime.now())
                    .build();
            messageMapper.insert(assistantMessage);

            updateConversationContextCache(conversationId, request.getContent(), cacheEntry.getAnswer());
            log.info("QA_CACHE_HIT questionHash={} conversationId={}", questionHash, conversationId);
            return Flux.just(cacheEntry.getAnswer());
        }
        log.info("QA_CACHE_MISS questionHash={} conversationId={}", questionHash, conversationId);

        KnowledgeRetrievalService.RetrievalResult retrieval = knowledgeRetrievalService.retrieve(request.getContent());
        String knowledgeContext = knowledgeRetrievalService.buildContext(retrieval);

        List<ChatMessage> chatMessages;
        Optional<List<ChatMessage>> cachedContext = chatCacheService.getConversationContext(conversationId);
        if (cachedContext.isPresent() && !cachedContext.get().isEmpty()) {
            chatMessages = new ArrayList<>(cachedContext.get());
            chatMessages.add(ChatMessage.user(request.getContent()));
            log.info("CTX_CACHE_HIT conversationId={}", conversationId);
        } else {
            List<Message> history = messageMapper.selectByConversationIdOrderByCreatedAt(conversationId);
            chatMessages = history.stream()
                    .map(m -> new ChatMessage(m.getRole(), m.getContent()))
                    .collect(Collectors.toCollection(ArrayList::new));
            chatMessages = compressConversationContext(chatMessages);
            log.info("CTX_CACHE_MISS conversationId={}, fallback=db", conversationId);
        }

        String systemPrompt = SYSTEM_PROMPT;
        if (!knowledgeContext.isEmpty()) {
            systemPrompt = systemPrompt + "\n\n" + knowledgeContext;
        }

        StringBuilder responseBuilder = new StringBuilder();
        String sourcesJson = buildSourcesJson(retrieval);

        return llmProviderFactory.getProvider()
                .chatStream(chatMessages, systemPrompt)
                .doOnNext(responseBuilder::append)
                .doOnComplete(() -> {
                    String answer = responseBuilder.toString();

                    Message assistantMessage = Message.builder()
                            .conversationId(conversationId)
                            .role("assistant")
                            .content(answer)
                            .sources(sourcesJson)
                            .createdAt(LocalDateTime.now())
                            .build();
                    messageMapper.insert(assistantMessage);

                    chatCacheService.cacheAnswer(questionHash, answer, sourcesJson);
                    updateConversationContextCache(conversationId, request.getContent(), answer);

                    log.debug("Assistant message saved for conversation {}", conversationId);
                })
                .doOnError(e -> log.error("Error during chat stream: {}", e.getMessage()));
    }

    public List<ConversationVO> getConversations(Long userId) {
        List<Conversation> conversations = conversationMapper.selectByUserIdOrderByUpdatedAtDesc(userId);
        return conversations.stream()
                .map(this::toConversationVO)
                .collect(Collectors.toList());
    }

    public List<MessageVO> getMessages(Long userId, Long conversationId) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null || !conversation.getUserId().equals(userId)) {
            throw new BusinessException(404, "Conversation not found");
        }

        List<Message> messages = messageMapper.selectByConversationIdOrderByCreatedAt(conversationId);
        return messages.stream()
                .map(this::toMessageVO)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteConversation(Long userId, Long conversationId) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null || !conversation.getUserId().equals(userId)) {
            throw new BusinessException(404, "Conversation not found");
        }

        messageMapper.deleteByConversationId(conversationId);
        conversationMapper.deleteById(conversationId);
        log.info("Conversation {} deleted by user {}", conversationId, userId);
    }

    public String generateLearningPath(String prompt) {
        try {
            log.info("Calling AI to generate learning path with prompt: {}", prompt);

            List<ChatMessage> messages = new ArrayList<>();
            messages.add(new ChatMessage("user", prompt));

            String response = llmProviderFactory.getProvider().chat(messages, SYSTEM_PROMPT);

            log.info("AI response for learning path received, length: {}", response != null ? response.length() : 0);
            return response;

        } catch (Exception e) {
            log.error("Failed to generate learning path: {}", e.getMessage(), e);
            throw new BusinessException(500, "AI生成学习路径失败: " + e.getMessage());
        }
    }

    private String generateTitle(String content) {
        if (content.length() <= 50) {
            return content;
        }
        return content.substring(0, 47) + "...";
    }

    private String createQuestionHash(String content) {
        String normalized = content == null ? "" : content.trim().toLowerCase();
        return DigestUtils.md5DigestAsHex(normalized.getBytes(StandardCharsets.UTF_8));
    }

    private void updateConversationContextCache(Long conversationId, String question, String answer) {
        List<ChatMessage> context = chatCacheService.getConversationContext(conversationId)
                .map(ArrayList::new)
                .orElseGet(ArrayList::new);

        context.add(ChatMessage.user(question));
        context.add(ChatMessage.assistant(answer));
        context = compressConversationContext(context);

        chatCacheService.saveConversationContext(conversationId, context);
    }

    private List<ChatMessage> compressConversationContext(List<ChatMessage> messages) {
        if (messages.size() <= 8) {
            return new ArrayList<>(messages);
        }

        List<ChatMessage> compressed = new ArrayList<>();
        List<ChatMessage> earlierMessages = messages.subList(0, Math.max(0, messages.size() - 6));
        String summary = earlierMessages.stream()
                .map(m -> ("user".equals(m.getRole()) ? "用户" : "助手") + "：" + m.getContent().replaceAll("\\s+", " "))
                .map(text -> text.length() > 120 ? text.substring(0, 120) + "..." : text)
                .collect(Collectors.joining("；"));

        if (!summary.isBlank()) {
            compressed.add(ChatMessage.system("以下是更早对话摘要，请保持上下文一致：" + summary));
        }

        compressed.addAll(messages.subList(Math.max(0, messages.size() - 6), messages.size()));
        return compressed;
    }

    private String buildSourcesJson(KnowledgeRetrievalService.RetrievalResult retrieval) {
        List<String> items = new ArrayList<>();
        retrieval.documents().forEach(doc -> items.add(String.format(
                "{\"type\":\"document\",\"id\":\"%s\",\"title\":\"%s\"}",
                escapeJson(String.valueOf(doc.getId())), escapeJson(doc.getTitle()))));
        retrieval.nodes().forEach(node -> items.add(String.format(
                "{\"type\":\"node\",\"id\":\"%s\",\"title\":\"%s\"}",
                escapeJson(node.getId()), escapeJson(node.getName()))));
        return items.isEmpty() ? null : items.stream().collect(Collectors.joining(",", "[", "]"));
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", " ")
                .replace("\r", " ");
    }

    private ConversationVO toConversationVO(Conversation conversation) {
        return ConversationVO.builder()
                .id(conversation.getId())
                .title(conversation.getTitle())
                .createdAt(conversation.getCreatedAt())
                .updatedAt(conversation.getUpdatedAt())
                .build();
    }

    private MessageVO toMessageVO(Message message) {
        return MessageVO.builder()
                .id(message.getId())
                .conversationId(message.getConversationId())
                .role(message.getRole())
                .content(message.getContent())
                .sources(message.getSources())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
