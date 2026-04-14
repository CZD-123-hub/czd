package com.coding.assistant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.coding.assistant.dto.FeedbackRequest;
import com.coding.assistant.entity.Feedback;
import com.coding.assistant.exception.BusinessException;
import com.coding.assistant.mapper.FeedbackMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final FeedbackMapper feedbackMapper;

    @Transactional
    public void submit(Long userId, FeedbackRequest request) {
        // Check for duplicate feedback (message_id + user_id)
        Long existingCount = feedbackMapper.selectCount(
                new LambdaQueryWrapper<Feedback>()
                        .eq(Feedback::getMessageId, request.getMessageId())
                        .eq(Feedback::getUserId, userId)
        );

        if (existingCount > 0) {
            throw new BusinessException(400, "Feedback already submitted for this message");
        }

        Feedback feedback = Feedback.builder()
                .messageId(request.getMessageId())
                .userId(userId)
                .rating(request.getRating())
                .comment(request.getComment())
                .createdAt(LocalDateTime.now())
                .build();

        feedbackMapper.insert(feedback);
        log.info("Feedback submitted: messageId={}, userId={}, rating={}", request.getMessageId(), userId, request.getRating());
    }
}