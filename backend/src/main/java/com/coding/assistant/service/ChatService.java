package com.coding.assistant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.coding.assistant.ai.ChatMessage;
import com.coding.assistant.ai.LlmProviderFactory;
import com.coding.assistant.dto.ConversationVO;
import com.coding.assistant.dto.EdgeVO;
import com.coding.assistant.dto.MessageVO;
import com.coding.assistant.dto.ChatSendRequest;
import com.coding.assistant.entity.Conversation;
import com.coding.assistant.entity.Feedback;
import com.coding.assistant.entity.Message;
import com.coding.assistant.exception.BusinessException;
import com.coding.assistant.exception.ErrorCode;
import com.coding.assistant.mapper.ConversationMapper;
import com.coding.assistant.mapper.FeedbackMapper;
import com.coding.assistant.mapper.MessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final FeedbackMapper feedbackMapper;
    private final LlmProviderFactory llmProviderFactory;
    private final KnowledgeRetrievalService knowledgeRetrievalService;
    private final ChatCacheService chatCacheService;
    private final QueryRetrievalLogService queryRetrievalLogService;

    private static final String SYSTEM_PROMPT = """
            You are an intelligent coding learning assistant.
            Always answer in Chinese unless the user explicitly requests another language.
            Prioritize retrieved knowledge context over generic knowledge.
            When graph relationships are provided, use node and relation evidence in the explanation.
            If the retrieved knowledge is insufficient, clearly say which parts are inferred.
            Keep answers accurate, concrete, and focused on programming learning.
            Use the following structure when possible:
            1. Conclusion
            2. Explanation
            3. Implementation steps
            4. Example code
            5. Common pitfalls
            6. References
            Never fabricate configuration keys, API names, or framework behavior.""";

    /**
     * 问答主流程（流式）：
     * 1) 创建/校验会话并写入用户消息；
     * 2) 尝试命中 QA 缓存，命中则直接返回；
     * 3) 未命中则执行 RAG 检索并组装系统上下文；
     * 4) 调用 LLM 流式生成，完成后写入 assistant 消息与检索日志。
     */
    @Transactional
    public Flux<ChatStreamEvent> sendMessage(Long userId, ChatSendRequest request) {
        Conversation conversation;
        // 新会话：首次提问时创建 conversation
        if (request.getConversationId() == null) {
            conversation = Conversation.builder()
                    .userId(userId)
                    .title(generateTitle(request.getContent()))
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            conversationMapper.insert(conversation);
        } else {
            // 旧会话：校验归属并更新时间
            conversation = conversationMapper.selectById(request.getConversationId());
            if (conversation == null || !conversation.getUserId().equals(userId)) {
                throw new BusinessException(
                        ErrorCode.NOT_FOUND,
                        ErrorCode.BIZ_CONVERSATION_NOT_FOUND,
                        "Conversation not found"
                );
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
        // 问题哈希用于 QA 缓存命中（归一化后 MD5）
        String questionHash = createQuestionHash(request.getContent());
        // 先发 meta 事件，让前端尽早拿到 conversationId
        ChatStreamEvent metaEvent = new ChatStreamEvent("meta", buildMetaJson(conversationId));

        // 第一层：同问同答缓存命中则短路（无需再次检索与调用 LLM）
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
            queryRetrievalLogService.logCacheHit(
                    userId,
                    conversationId,
                    userMessage.getId(),
                    assistantMessage.getId(),
                    request.getContent(),
                    questionHash,
                    cacheEntry.getSourcesJson()
            );
            log.info("QA_CACHE_HIT questionHash={} conversationId={}", questionHash, conversationId);
            return Flux.just(
                    metaEvent,
                    new ChatStreamEvent("delta", cacheEntry.getAnswer()),
                    new ChatStreamEvent("done", buildDoneJson(assistantMessage.getId()))
            );
        }
        log.info("QA_CACHE_MISS questionHash={} conversationId={}", questionHash, conversationId);

        // 第二层：RAG 检索（文档 + 图节点 + 图关系），并构造注入到系统提示词的证据上下文
        // RAG 检索入口：文档侧会在内部进行 embedding 向量检索与相似度计算。
        KnowledgeRetrievalService.RetrievalResult retrieval = knowledgeRetrievalService.retrieve(request.getContent());
        String knowledgeContext = knowledgeRetrievalService.buildContext(retrieval);

        List<ChatMessage> chatMessages;
        // 第三层：会话上下文缓存命中则直接复用，未命中则回源 DB 并压缩上下文
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

        // 将检索到的证据拼接到系统提示词，指导模型优先依据检索结果回答
        String systemPrompt = SYSTEM_PROMPT;
        if (!knowledgeContext.isEmpty()) {
            systemPrompt = systemPrompt + "\n\n" + knowledgeContext;
        }

        StringBuilder responseBuilder = new StringBuilder();
        String sourcesJson = buildSourcesJson(retrieval);
        final List<ChatMessage> finalChatMessages = chatMessages;
        final String finalSystemPrompt = systemPrompt;

        // SSE 流：按 delta 事件把模型输出逐块推给前端
        return Flux.create(sink -> {
            sink.next(metaEvent);
            Disposable llmStream = llmProviderFactory.getProvider()
                    .chatStream(finalChatMessages, finalSystemPrompt)
                    .subscribe(
                            chunk -> {
                                responseBuilder.append(chunk);
                                sink.next(new ChatStreamEvent("delta", chunk));
                            },
                            error -> {
                                log.error("Error during chat stream: {}", error.getMessage());
                                sink.error(error);
                            },
                            () -> {
                                try {
                                    String answer = responseBuilder.toString();

                                    // 生成完成后落库 assistant 消息（含 sources 证据 JSON）
                                    Message assistantMessage = Message.builder()
                                            .conversationId(conversationId)
                                            .role("assistant")
                                            .content(answer)
                                            .sources(sourcesJson)
                                            .createdAt(LocalDateTime.now())
                                            .build();
                                    messageMapper.insert(assistantMessage);

                                    // 回写缓存并持久化检索日志，便于下次命中与链路追踪
                                    chatCacheService.cacheAnswer(questionHash, answer, sourcesJson);
                                    updateConversationContextCache(conversationId, request.getContent(), answer);
                                    queryRetrievalLogService.logRetrieved(
                                            userId,
                                            conversationId,
                                            userMessage.getId(),
                                            assistantMessage.getId(),
                                            request.getContent(),
                                            questionHash,
                                            retrieval
                                    );

                                    // done 事件携带 assistantMessageId，前端可用于反馈/引用
                                    sink.next(new ChatStreamEvent("done", buildDoneJson(assistantMessage.getId())));
                                    sink.complete();
                                    log.debug("Assistant message saved for conversation {}", conversationId);
                                } catch (Exception persistError) {
                                    sink.error(persistError);
                                }
                            }
                    );

            sink.onCancel(() -> {
                if (!llmStream.isDisposed()) {
                    llmStream.dispose();
                }
                log.info("Chat stream canceled by client. conversationId={}", conversationId);
            });

            sink.onDispose(() -> {
                if (!llmStream.isDisposed()) {
                    llmStream.dispose();
                }
            });
        }, FluxSink.OverflowStrategy.BUFFER);
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
            throw new BusinessException(
                    ErrorCode.NOT_FOUND,
                    ErrorCode.BIZ_CONVERSATION_NOT_FOUND,
                    "Conversation not found"
            );
        }

        List<Message> messages = messageMapper.selectByConversationIdOrderByCreatedAt(conversationId);
        if (messages.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> messageIds = messages.stream().map(Message::getId).toList();
        Map<Long, String> feedbackByMessageId = feedbackMapper.selectList(
                        new LambdaQueryWrapper<Feedback>()
                                .eq(Feedback::getUserId, userId)
                                .in(Feedback::getMessageId, messageIds)
                ).stream()
                .collect(Collectors.toMap(Feedback::getMessageId, Feedback::getRating, (a, b) -> a));

        return messages.stream()
                .map(message -> toMessageVO(message, feedbackByMessageId.get(message.getId())))
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteConversation(Long userId, Long conversationId) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null || !conversation.getUserId().equals(userId)) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND,
                    ErrorCode.BIZ_CONVERSATION_NOT_FOUND,
                    "Conversation not found"
            );
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
            throw new BusinessException(
                    ErrorCode.INTERNAL_SERVER_ERROR,
                    ErrorCode.BIZ_AI_GENERATION_FAILED,
                    "AI failed to generate learning path: " + e.getMessage()
            );
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

    /**
     * 维护会话上下文缓存：追加本轮问答后再做上下文压缩。
     */
    private void updateConversationContextCache(Long conversationId, String question, String answer) {
        List<ChatMessage> context = chatCacheService.getConversationContext(conversationId)
                .map(ArrayList::new)
                .orElseGet(ArrayList::new);

        context.add(ChatMessage.user(question));
        context.add(ChatMessage.assistant(answer));
        context = compressConversationContext(context);

        chatCacheService.saveConversationContext(conversationId, context);
    }

    /**
     * 控制上下文长度：超过阈值时把更早消息摘要为一条 system 消息。
     */
    private List<ChatMessage> compressConversationContext(List<ChatMessage> messages) {
        if (messages.size() <= 8) {
            return new ArrayList<>(messages);
        }

        List<ChatMessage> compressed = new ArrayList<>();
        List<ChatMessage> earlierMessages = messages.subList(0, Math.max(0, messages.size() - 6));
        String summary = earlierMessages.stream()
                .map(m -> ("user".equals(m.getRole()) ? "user: " : "assistant: ") + m.getContent().replaceAll("\\s+", " "))
                .map(text -> text.length() > 120 ? text.substring(0, 120) + "..." : text)
                .collect(Collectors.joining(" | "));

        if (!summary.isBlank()) {
            compressed.add(ChatMessage.system("Earlier conversation summary, keep context consistent: " + summary));
        }

        compressed.addAll(messages.subList(Math.max(0, messages.size() - 6), messages.size()));
        return compressed;
    }

    /**
     * 将检索结果序列化为 sources JSON，存入 message.sources，供前端展示和日志回放。
     */
    private String buildSourcesJson(KnowledgeRetrievalService.RetrievalResult retrieval) {
        List<String> items = new ArrayList<>();
        Map<String, String> nodeNameById = retrieval.nodes().stream()
                .collect(Collectors.toMap(
                        node -> node.getId() == null ? "" : node.getId(),
                        node -> node.getName() == null ? "" : node.getName(),
                        (a, b) -> a
                ));

        retrieval.documents().forEach(doc -> items.add(String.format(
                "{\"type\":\"document\",\"id\":\"%s\",\"title\":\"%s\",\"excerpt\":\"%s\",\"chunkIndex\":%s,\"score\":%s}",
                escapeJson(String.valueOf(doc.getId())),
                escapeJson(doc.getTitle()),
                escapeJson(buildDocExcerpt(doc.getContent())),
                doc.getHitChunkIndex() == null ? "null" : String.valueOf(doc.getHitChunkIndex()),
                doc.getHitScore() == null ? "null" : String.format(Locale.ROOT, "%.6f", doc.getHitScore()))));
        retrieval.nodes().forEach(node -> items.add(String.format(
                "{\"type\":\"node\",\"id\":\"%s\",\"title\":\"%s\",\"excerpt\":\"%s\"}",
                escapeJson(node.getId()),
                escapeJson(node.getName()),
                escapeJson(buildNodeExcerpt(node.getDescription(), node.getCategory())))));
        retrieval.edges().forEach(edge -> items.add(String.format(
                "{\"type\":\"relation\",\"id\":\"%s\",\"title\":\"%s\",\"excerpt\":\"%s\",\"relationType\":\"%s\"}",
                escapeJson(buildRelationId(edge)),
                escapeJson(buildRelationTitle(edge, nodeNameById)),
                escapeJson(buildRelationExcerpt(edge)),
                escapeJson(edge.getType()))));
        return items.isEmpty() ? null : items.stream().collect(Collectors.joining(",", "[", "]"));
    }

    private String buildDocExcerpt(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String normalized = content.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 120 ? normalized : normalized.substring(0, 120) + "...";
    }

    private String buildNodeExcerpt(String description, String category) {
        String desc = description == null ? "" : description.trim();
        if (!desc.isEmpty()) {
            return desc.length() <= 120 ? desc : desc.substring(0, 120) + "...";
        }
        return category == null || category.isBlank() ? "" : "category: " + category.trim();
    }

    private String buildRelationId(EdgeVO edge) {
        return edge.getSource() + "->" + edge.getType() + "->" + edge.getTarget();
    }

    private String buildRelationTitle(EdgeVO edge, Map<String, String> nodeNameById) {
        String sourceName = nodeNameById.getOrDefault(edge.getSource(), edge.getSource());
        String targetName = nodeNameById.getOrDefault(edge.getTarget(), edge.getTarget());
        return sourceName + " --" + edge.getType() + "--> " + targetName;
    }

    private String buildRelationExcerpt(EdgeVO edge) {
        return "Graph relation evidence: " + edge.getType() + ", " + edge.getSource() + " -> " + edge.getTarget() + ".";
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

    private String buildMetaJson(Long conversationId) {
        long safeConversationId = conversationId == null ? 0L : conversationId;
        return "{\"conversationId\":" + safeConversationId + "}";
    }

    private String buildDoneJson(Long assistantMessageId) {
        long safeAssistantMessageId = assistantMessageId == null ? 0L : assistantMessageId;
        return "{\"assistantMessageId\":" + safeAssistantMessageId + "}";
    }

    private ConversationVO toConversationVO(Conversation conversation) {
        return ConversationVO.builder()
                .id(conversation.getId())
                .title(conversation.getTitle())
                .createdAt(conversation.getCreatedAt())
                .updatedAt(conversation.getUpdatedAt())
                .build();
    }

    private MessageVO toMessageVO(Message message, String feedbackRating) {
        return MessageVO.builder()
                .id(message.getId())
                .conversationId(message.getConversationId())
                .role(message.getRole())
                .content(message.getContent())
                .sources(message.getSources())
                .feedbackRating(feedbackRating)
                .createdAt(message.getCreatedAt())
                .build();
    }

    public record ChatStreamEvent(String event, String data) {}
}


