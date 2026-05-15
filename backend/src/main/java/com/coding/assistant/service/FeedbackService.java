package com.coding.assistant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.coding.assistant.dto.FeedbackRequest;
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

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final FeedbackMapper feedbackMapper;
    private final MessageMapper messageMapper;
    private final ConversationMapper conversationMapper;

    private static final Set<String> VALID_RATINGS = Set.of("useful", "useless");

    @Transactional
    public void submit(Long userId, FeedbackRequest request) {
        String normalizedRating = normalizeRating(request.getRating());

        Message message = messageMapper.selectById(request.getMessageId());
        if (message == null) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND,
                    ErrorCode.BIZ_MESSAGE_NOT_FOUND,
                    "Message not found"
            );
        }
        if (!"assistant".equals(message.getRole())) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    ErrorCode.BIZ_BAD_REQUEST,
                    "Feedback can only be submitted for assistant messages"
            );
        }

        Conversation conversation = conversationMapper.selectById(message.getConversationId());
        if (conversation == null || !conversation.getUserId().equals(userId)) {
            throw new BusinessException(
                    ErrorCode.FORBIDDEN,
                    ErrorCode.BIZ_FORBIDDEN,
                    "No permission to feedback this message"
            );
        }

        Feedback existingFeedback = feedbackMapper.selectOne(
                new LambdaQueryWrapper<Feedback>()
                        .eq(Feedback::getMessageId, request.getMessageId())
                        .eq(Feedback::getUserId, userId)
        );

        if (normalizedRating == null) {
            if (existingFeedback != null) {
                feedbackMapper.deleteById(existingFeedback.getId());
                log.info("Feedback canceled: messageId={}, userId={}", request.getMessageId(), userId);
            } else {
                log.info("Feedback cancel ignored: no existing feedback, messageId={}, userId={}", request.getMessageId(), userId);
            }
            return;
        }

        if (existingFeedback == null) {
            Feedback feedback = Feedback.builder()
                    .messageId(request.getMessageId())
                    .userId(userId)
                    .rating(normalizedRating)
                    .comment(request.getComment())
                    .createdAt(LocalDateTime.now())
                    .build();
            feedbackMapper.insert(feedback);
            log.info("Feedback submitted: messageId={}, userId={}, rating={}", request.getMessageId(), userId, normalizedRating);
            return;
        }

        existingFeedback.setRating(normalizedRating);
        existingFeedback.setComment(request.getComment());
        feedbackMapper.updateById(existingFeedback);
        log.info("Feedback updated: messageId={}, userId={}, rating={}", request.getMessageId(), userId, normalizedRating);
    }

    private String normalizeRating(String rawRating) {
        if (rawRating == null) {
            return null;
        }
        String normalized = rawRating.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || !VALID_RATINGS.contains(normalized)) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    ErrorCode.BIZ_BAD_REQUEST,
                    "Rating must be useful, useless, or null to cancel"
            );
        }
        return normalized;
    }
}
