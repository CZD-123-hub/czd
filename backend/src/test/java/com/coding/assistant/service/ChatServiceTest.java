package com.coding.assistant.service;

import com.coding.assistant.ai.LlmProvider;
import com.coding.assistant.ai.LlmProviderFactory;
import com.coding.assistant.dto.ChatSendRequest;
import com.coding.assistant.entity.Conversation;
import com.coding.assistant.entity.Message;
import com.coding.assistant.mapper.ConversationMapper;
import com.coding.assistant.mapper.FeedbackMapper;
import com.coding.assistant.mapper.MessageMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ConversationMapper conversationMapper;

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private FeedbackMapper feedbackMapper;

    @Mock
    private LlmProviderFactory llmProviderFactory;

    @Mock
    private KnowledgeRetrievalService knowledgeRetrievalService;

    @Mock
    private ChatCacheService chatCacheService;

    @Mock
    private QueryRetrievalLogService queryRetrievalLogService;

    @Mock
    private LlmProvider llmProvider;

    @InjectMocks
    private ChatService chatService;

    @Test
    void sendMessage_shouldReuseExistingConversationAndKeepConversationId() {
        Long userId = 1L;
        Long conversationId = 77L;
        Long assistantMessageId = 901L;

        Conversation existing = Conversation.builder()
                .id(conversationId)
                .userId(userId)
                .title("existing")
                .build();

        KnowledgeRetrievalService.RetrievalResult retrievalResult =
                new KnowledgeRetrievalService.RetrievalResult(List.of(), List.of(), List.of(), List.of());

        when(conversationMapper.selectById(conversationId)).thenReturn(existing);
        when(chatCacheService.getCachedAnswer(anyString())).thenReturn(Optional.empty());
        when(knowledgeRetrievalService.retrieve(anyString())).thenReturn(retrievalResult);
        when(knowledgeRetrievalService.buildContext(retrievalResult)).thenReturn("");
        when(chatCacheService.getConversationContext(anyLong())).thenReturn(Optional.empty());
        when(messageMapper.selectByConversationIdOrderByCreatedAt(conversationId)).thenReturn(List.of());
        when(llmProviderFactory.getProvider()).thenReturn(llmProvider);
        when(llmProvider.chatStream(any(), anyString())).thenReturn(Flux.just("assistant answer"));
        doAnswer(invocation -> {
            Message m = invocation.getArgument(0);
            if ("assistant".equals(m.getRole())) {
                m.setId(assistantMessageId);
            }
            return 1;
        }).when(messageMapper).insert(any(Message.class));

        ChatSendRequest request = ChatSendRequest.builder()
                .conversationId(conversationId)
                .content("how to use spring boot")
                .build();

        List<ChatService.ChatStreamEvent> events = chatService.sendMessage(userId, request).collectList().block();

        assertNotNull(events);
        assertEquals(3, events.size());
        assertEquals("meta", events.get(0).event());
        assertEquals("{\"conversationId\":77}", events.get(0).data());
        assertEquals("delta", events.get(1).event());
        assertEquals("assistant answer", events.get(1).data());
        assertEquals("done", events.get(2).event());
        assertEquals("{\"assistantMessageId\":901}", events.get(2).data());

        verify(conversationMapper, never()).insert(any());
        verify(conversationMapper).updateById(existing);

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageMapper, times(2)).insert(messageCaptor.capture());
        List<Message> savedMessages = messageCaptor.getAllValues();
        assertEquals("user", savedMessages.get(0).getRole());
        assertEquals(conversationId, savedMessages.get(0).getConversationId());
        assertEquals("assistant", savedMessages.get(1).getRole());
        assertEquals(conversationId, savedMessages.get(1).getConversationId());

        verify(chatCacheService).saveConversationContext(anyLong(), any());
    }

    @Test
    void sendMessage_shouldCreateConversationAndBindMessagesToNewConversation() {
        Long userId = 2L;
        Long newConversationId = 101L;
        Long assistantMessageId = 902L;

        doAnswer(invocation -> {
            Conversation c = invocation.getArgument(0);
            c.setId(newConversationId);
            return 1;
        }).when(conversationMapper).insert(any(Conversation.class));

        KnowledgeRetrievalService.RetrievalResult retrievalResult =
                new KnowledgeRetrievalService.RetrievalResult(List.of(), List.of(), List.of(), List.of());

        when(chatCacheService.getCachedAnswer(anyString())).thenReturn(Optional.empty());
        when(knowledgeRetrievalService.retrieve(anyString())).thenReturn(retrievalResult);
        when(knowledgeRetrievalService.buildContext(retrievalResult)).thenReturn("");
        when(chatCacheService.getConversationContext(anyLong())).thenReturn(Optional.empty());
        when(messageMapper.selectByConversationIdOrderByCreatedAt(newConversationId)).thenReturn(List.of());
        when(llmProviderFactory.getProvider()).thenReturn(llmProvider);
        when(llmProvider.chatStream(any(), anyString())).thenReturn(Flux.just("ok"));
        doAnswer(invocation -> {
            Message m = invocation.getArgument(0);
            if ("assistant".equals(m.getRole())) {
                m.setId(assistantMessageId);
            }
            return 1;
        }).when(messageMapper).insert(any(Message.class));

        ChatSendRequest request = ChatSendRequest.builder()
                .conversationId(null)
                .content("new chat question")
                .build();

        List<ChatService.ChatStreamEvent> events = chatService.sendMessage(userId, request).collectList().block();

        assertNotNull(events);
        assertEquals(3, events.size());
        assertEquals("meta", events.get(0).event());
        assertEquals("{\"conversationId\":101}", events.get(0).data());
        assertEquals("delta", events.get(1).event());
        assertEquals("ok", events.get(1).data());
        assertEquals("done", events.get(2).event());
        assertEquals("{\"assistantMessageId\":902}", events.get(2).data());

        verify(conversationMapper).insert(any(Conversation.class));
        verify(conversationMapper, never()).updateById(any());

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageMapper, times(2)).insert(messageCaptor.capture());
        List<Message> savedMessages = messageCaptor.getAllValues();
        assertEquals(newConversationId, savedMessages.get(0).getConversationId());
        assertEquals(newConversationId, savedMessages.get(1).getConversationId());
    }
}
