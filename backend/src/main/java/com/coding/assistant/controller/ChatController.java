package com.coding.assistant.controller;

import com.coding.assistant.dto.ApiResponse;
import com.coding.assistant.dto.ChatSendRequest;
import com.coding.assistant.dto.ConversationVO;
import com.coding.assistant.dto.FeedbackRequest;
import com.coding.assistant.dto.MessageVO;
import com.coding.assistant.security.SecurityUtil;
import com.coding.assistant.service.ChatService;
import com.coding.assistant.service.FeedbackService;
import com.coding.assistant.service.LearningActionType;
import com.coding.assistant.service.ProgressService;
import com.coding.assistant.service.RetrievalMetricsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final FeedbackService feedbackService;
    private final ProgressService progressService;
    private final RetrievalMetricsService retrievalMetricsService;

    @PostMapping(value = "/send", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> sendMessage(@Valid @RequestBody ChatSendRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        progressService.recordAction(userId, LearningActionType.CHAT, null);

        return chatService.sendMessage(userId, request)
                .map(event -> ServerSentEvent.<String>builder()
                        .event(event.event())
                        .data(event.data())
                        .build())
                .onErrorResume(ex -> Flux.just(
                        ServerSentEvent.<String>builder()
                                .event("error")
                                .data(ex.getMessage() == null || ex.getMessage().isBlank()
                                        ? "服务异常，请稍后重试"
                                        : ex.getMessage())
                                .build()
                ));
    }

    @GetMapping("/conversations")
    public ApiResponse<List<ConversationVO>> getConversations() {
        Long userId = SecurityUtil.getCurrentUserId();
        List<ConversationVO> conversations = chatService.getConversations(userId);
        return ApiResponse.success(conversations);
    }

    @GetMapping("/conversations/{id}/messages")
    public ApiResponse<List<MessageVO>> getMessages(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        List<MessageVO> messages = chatService.getMessages(userId, id);
        return ApiResponse.success(messages);
    }

    @DeleteMapping("/conversations/{id}")
    public ApiResponse<Void> deleteConversation(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        chatService.deleteConversation(userId, id);
        return ApiResponse.success();
    }

    @PostMapping("/feedback")
    public ApiResponse<Void> submitFeedback(@Valid @RequestBody FeedbackRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        feedbackService.submit(userId, request);
        progressService.recordAction(userId, LearningActionType.FEEDBACK, String.valueOf(request.getMessageId()));
        return ApiResponse.success();
    }

    @GetMapping("/retrieval-metrics")
    public ApiResponse<Map<String, Object>> getRetrievalMetrics() {
        Long userId = SecurityUtil.getCurrentUserId();
        Map<String, Object> snapshot = new LinkedHashMap<>(retrievalMetricsService.snapshot());
        snapshot.put("viewerUserId", userId);
        return ApiResponse.success(snapshot);
    }

    @PostMapping("/retrieval-metrics/reset")
    public ApiResponse<Void> resetRetrievalMetrics() {
        SecurityUtil.getCurrentUserId();
        retrievalMetricsService.reset();
        return ApiResponse.success();
    }
}
