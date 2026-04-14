package com.coding.assistant.controller;

import com.coding.assistant.dto.*;
import com.coding.assistant.security.SecurityUtil;
import com.coding.assistant.service.ChatService;
import com.coding.assistant.service.FeedbackService;
import com.coding.assistant.service.ProgressService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final FeedbackService feedbackService;
    private final ProgressService progressService;

    @PostMapping(value = "/send", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> sendMessage(@Valid @RequestBody ChatSendRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();

        // Record chat action
        progressService.recordAction(userId, "chat", null);

        return chatService.sendMessage(userId, request)
                .map(content -> ServerSentEvent.<String>builder()
                        .data(content)
                        .build())
                .concatWith(Flux.just(ServerSentEvent.<String>builder()
                        .event("done")
                        .data("[DONE]")
                        .build()));
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
        progressService.recordAction(userId, "feedback", String.valueOf(request.getMessageId()));
        return ApiResponse.success();
    }
}