package com.coding.assistant.controller;

import com.coding.assistant.dto.ConversationVO;
import com.coding.assistant.dto.FeedbackRequest;
import com.coding.assistant.dto.MessageVO;
import com.coding.assistant.exception.GlobalExceptionHandler;
import com.coding.assistant.security.JwtUserDetails;
import com.coding.assistant.service.ChatService;
import com.coding.assistant.service.FeedbackService;
import com.coding.assistant.service.LearningActionType;
import com.coding.assistant.service.ProgressService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ChatControllerContractTest {

    @Mock
    private ChatService chatService;

    @Mock
    private FeedbackService feedbackService;

    @Mock
    private ProgressService progressService;

    @InjectMocks
    private ChatController chatController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        this.mockMvc = MockMvcBuilders.standaloneSetup(chatController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();

        JwtUserDetails principal = new JwtUserDetails(1L, "tester");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList())
        );
    }

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getConversations_shouldReturnConversationArrayContract() throws Exception {
        when(chatService.getConversations(1L)).thenReturn(List.of(
                ConversationVO.builder()
                        .id(101L)
                        .title("Spring Boot with Redis")
                        .createdAt(LocalDateTime.of(2026, 4, 20, 14, 0))
                        .updatedAt(LocalDateTime.of(2026, 4, 20, 14, 5))
                        .build()
        ));

        mockMvc.perform(get("/api/chat/conversations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data[0].id").value(101))
                .andExpect(jsonPath("$.data[0].title").value("Spring Boot with Redis"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/chat/conversations"));

        verify(chatService).getConversations(1L);
    }

    @Test
    void getMessages_shouldReturnMessageArrayContract() throws Exception {
        when(chatService.getMessages(1L, 101L)).thenReturn(List.of(
                MessageVO.builder()
                        .id(5001L)
                        .conversationId(101L)
                        .role("assistant")
                        .content("Use Redis cache layer")
                        .sources("[{\"type\":\"document\"}]")
                        .feedbackRating("useful")
                        .createdAt(LocalDateTime.of(2026, 4, 20, 14, 6))
                        .build()
        ));

        mockMvc.perform(get("/api/chat/conversations/101/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data[0].id").value(5001))
                .andExpect(jsonPath("$.data[0].conversationId").value(101))
                .andExpect(jsonPath("$.data[0].role").value("assistant"))
                .andExpect(jsonPath("$.data[0].feedbackRating").value("useful"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/chat/conversations/101/messages"));

        verify(chatService).getMessages(1L, 101L);
    }

    @Test
    void submitFeedback_shouldReturnSuccessContract() throws Exception {
        mockMvc.perform(post("/api/chat/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "messageId": 5001,
                                  "rating": "useful",
                                  "comment": "helpful answer"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/chat/feedback"));

        ArgumentCaptor<FeedbackRequest> feedbackCaptor = ArgumentCaptor.forClass(FeedbackRequest.class);
        verify(feedbackService).submit(org.mockito.ArgumentMatchers.eq(1L), feedbackCaptor.capture());
        assertEquals(5001L, feedbackCaptor.getValue().getMessageId());
        assertEquals("useful", feedbackCaptor.getValue().getRating());
        verify(progressService).recordAction(1L, LearningActionType.FEEDBACK, "5001");
    }

    @Test
    void submitFeedback_shouldReturnValidationErrorContract_whenRatingInvalid() throws Exception {
        mockMvc.perform(post("/api/chat/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "messageId": 5001,
                                  "rating": "like"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.error.bizCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message", containsString("rating")))
                .andExpect(jsonPath("$.error.details").isArray())
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/chat/feedback"));

        verify(feedbackService, never()).submit(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any(FeedbackRequest.class));
    }

    @Test
    void deleteConversation_shouldReturnSuccessContract() throws Exception {
        mockMvc.perform(delete("/api/chat/conversations/101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/chat/conversations/101"));

        verify(chatService).deleteConversation(1L, 101L);
    }
}
