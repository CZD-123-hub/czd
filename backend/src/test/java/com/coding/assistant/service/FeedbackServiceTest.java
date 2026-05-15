package com.coding.assistant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.coding.assistant.dto.FeedbackRequest;
import com.coding.assistant.entity.Conversation;
import com.coding.assistant.entity.Feedback;
import com.coding.assistant.entity.Message;
import com.coding.assistant.exception.BusinessException;
import com.coding.assistant.mapper.ConversationMapper;
import com.coding.assistant.mapper.FeedbackMapper;
import com.coding.assistant.mapper.MessageMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedbackServiceTest {

    @Mock
    private FeedbackMapper feedbackMapper;

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private ConversationMapper conversationMapper;

    @InjectMocks
    private FeedbackService feedbackService;

    @Test
    void submit_shouldInsertFeedbackWhenFirstSubmit() {
        Long userId = 1L;
        Long messageId = 100L;

        when(messageMapper.selectById(messageId)).thenReturn(mockAssistantMessage(messageId, 200L));
        when(conversationMapper.selectById(200L)).thenReturn(mockConversation(200L, userId));
        when(feedbackMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        FeedbackRequest request = FeedbackRequest.builder()
                .messageId(messageId)
                .rating("useful")
                .comment("great")
                .build();

        feedbackService.submit(userId, request);

        ArgumentCaptor<Feedback> captor = ArgumentCaptor.forClass(Feedback.class);
        verify(feedbackMapper).insert(captor.capture());
        verify(feedbackMapper, never()).updateById(any());

        Feedback saved = captor.getValue();
        assertEquals(messageId, saved.getMessageId());
        assertEquals(userId, saved.getUserId());
        assertEquals("useful", saved.getRating());
        assertEquals("great", saved.getComment());
        assertNotNull(saved.getCreatedAt());
    }

    @Test
    void submit_shouldUpdateFeedbackWhenAlreadyExists() {
        Long userId = 1L;
        Long messageId = 100L;

        Feedback existing = Feedback.builder()
                .id(9L)
                .messageId(messageId)
                .userId(userId)
                .rating("useful")
                .comment("old")
                .build();

        when(messageMapper.selectById(messageId)).thenReturn(mockAssistantMessage(messageId, 200L));
        when(conversationMapper.selectById(200L)).thenReturn(mockConversation(200L, userId));
        when(feedbackMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        FeedbackRequest request = FeedbackRequest.builder()
                .messageId(messageId)
                .rating("useless")
                .comment("new")
                .build();

        feedbackService.submit(userId, request);

        verify(feedbackMapper, never()).insert(any());
        verify(feedbackMapper).updateById(existing);

        assertEquals("useless", existing.getRating());
        assertEquals("new", existing.getComment());
    }

    @Test
    void submit_shouldDeleteFeedbackWhenCancel() {
        Long userId = 1L;
        Long messageId = 100L;

        Feedback existing = Feedback.builder()
                .id(9L)
                .messageId(messageId)
                .userId(userId)
                .rating("useful")
                .build();

        when(messageMapper.selectById(messageId)).thenReturn(mockAssistantMessage(messageId, 200L));
        when(conversationMapper.selectById(200L)).thenReturn(mockConversation(200L, userId));
        when(feedbackMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        FeedbackRequest request = FeedbackRequest.builder()
                .messageId(messageId)
                .rating(null)
                .build();

        feedbackService.submit(userId, request);

        verify(feedbackMapper).deleteById(9L);
        verify(feedbackMapper, never()).insert(any());
        verify(feedbackMapper, never()).updateById(any());
    }

    @Test
    void submit_shouldThrowWhenMessageNotFound() {
        Long userId = 1L;
        Long messageId = 100L;

        when(messageMapper.selectById(messageId)).thenReturn(null);

        FeedbackRequest request = FeedbackRequest.builder()
                .messageId(messageId)
                .rating("useful")
                .build();

        BusinessException exception = assertThrows(BusinessException.class, () -> feedbackService.submit(userId, request));

        assertEquals(404, exception.getCode());
        assertEquals("Message not found", exception.getMessage());
    }

    @Test
    void submit_shouldThrowWhenMessageIsNotAssistant() {
        Long userId = 1L;
        Long messageId = 100L;

        Message userMessage = Message.builder()
                .id(messageId)
                .conversationId(200L)
                .role("user")
                .build();

        when(messageMapper.selectById(messageId)).thenReturn(userMessage);

        FeedbackRequest request = FeedbackRequest.builder()
                .messageId(messageId)
                .rating("useful")
                .build();

        BusinessException exception = assertThrows(BusinessException.class, () -> feedbackService.submit(userId, request));

        assertEquals(400, exception.getCode());
        assertEquals("Feedback can only be submitted for assistant messages", exception.getMessage());
    }

    @Test
    void submit_shouldThrowWhenMessageDoesNotBelongToUser() {
        Long userId = 1L;
        Long messageId = 100L;

        when(messageMapper.selectById(messageId)).thenReturn(mockAssistantMessage(messageId, 200L));
        when(conversationMapper.selectById(200L)).thenReturn(mockConversation(200L, 2L));

        FeedbackRequest request = FeedbackRequest.builder()
                .messageId(messageId)
                .rating("useful")
                .build();

        BusinessException exception = assertThrows(BusinessException.class, () -> feedbackService.submit(userId, request));

        assertEquals(403, exception.getCode());
        assertEquals("No permission to feedback this message", exception.getMessage());
    }

    @Test
    void submit_shouldThrowWhenRatingInvalid() {
        FeedbackRequest request = FeedbackRequest.builder()
                .messageId(100L)
                .rating("bad")
                .build();

        BusinessException exception = assertThrows(BusinessException.class, () -> feedbackService.submit(1L, request));

        assertEquals(400, exception.getCode());
        assertEquals("Rating must be useful, useless, or null to cancel", exception.getMessage());
        verifyNoInteractions(messageMapper, conversationMapper, feedbackMapper);
    }

    private Message mockAssistantMessage(Long messageId, Long conversationId) {
        return Message.builder()
                .id(messageId)
                .conversationId(conversationId)
                .role("assistant")
                .build();
    }

    private Conversation mockConversation(Long conversationId, Long userId) {
        return Conversation.builder()
                .id(conversationId)
                .userId(userId)
                .build();
    }
}

