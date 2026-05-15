package com.coding.assistant.controller;

import com.coding.assistant.dto.LearningNodeVO;
import com.coding.assistant.dto.LearningPathVO;
import com.coding.assistant.dto.PathGenerateRequest;
import com.coding.assistant.exception.GlobalExceptionHandler;
import com.coding.assistant.security.JwtUserDetails;
import com.coding.assistant.service.LearningActionType;
import com.coding.assistant.service.PathService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PathControllerContractTest {

    @Mock
    private PathService pathService;

    @Mock
    private ProgressService progressService;

    @InjectMocks
    private PathController pathController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        this.mockMvc = MockMvcBuilders.standaloneSetup(pathController)
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
    void generate_shouldReturnLearningPathContract() throws Exception {
        LearningPathVO learningPath = LearningPathVO.builder()
                .id(88L)
                .target("Spring Boot")
                .status("active")
                .nodes(List.of(
                        LearningNodeVO.builder()
                                .id(501L)
                                .knowledgeId("java-basics")
                                .knowledgeName("Java Basics")
                                .nodeOrder(1)
                                .status("todo")
                                .resourceUrls(List.of("https://example.com/java"))
                                .build()
                ))
                .createdAt(LocalDateTime.of(2026, 4, 20, 10, 0))
                .build();
        when(pathService.generate(eq(1L), any(PathGenerateRequest.class))).thenReturn(learningPath);

        mockMvc.perform(post("/api/path/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "target": "Spring Boot",
                                  "knownKnowledgeIds": ["java-basic-syntax"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.id").value(88))
                .andExpect(jsonPath("$.data.target").value("Spring Boot"))
                .andExpect(jsonPath("$.data.status").value("active"))
                .andExpect(jsonPath("$.data.nodes[0].id").value(501))
                .andExpect(jsonPath("$.data.nodes[0].knowledgeId").value("java-basics"))
                .andExpect(jsonPath("$.data.nodes[0].knowledgeName").value("Java Basics"))
                .andExpect(jsonPath("$.data.nodes[0].nodeOrder").value(1))
                .andExpect(jsonPath("$.data.nodes[0].status").value("todo"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/path/generate"));

        ArgumentCaptor<PathGenerateRequest> requestCaptor = ArgumentCaptor.forClass(PathGenerateRequest.class);
        verify(pathService).generate(eq(1L), requestCaptor.capture());
        assertEquals("Spring Boot", requestCaptor.getValue().getTarget());
        assertEquals(List.of("java-basic-syntax"), requestCaptor.getValue().getKnownKnowledgeIds());
        verify(progressService).recordAction(1L, LearningActionType.PATH_LEARN, "88");
    }

    @Test
    void updateNodeStatus_shouldReturnValidationErrorContract_whenStatusInvalid() throws Exception {
        mockMvc.perform(put("/api/path/node/12/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "inprogress"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.error.bizCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message", containsString("status")))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/path/node/12/status"));

        verify(pathService, never()).updateNodeStatus(anyLong(), anyLong(), anyString());
    }

    @Test
    void list_shouldReturnArrayContract() throws Exception {
        LearningPathVO learningPath = LearningPathVO.builder()
                .id(99L)
                .target("Redis")
                .status("in_progress")
                .nodes(List.of())
                .createdAt(LocalDateTime.of(2026, 4, 20, 11, 0))
                .build();
        when(pathService.list(1L)).thenReturn(List.of(learningPath));

        mockMvc.perform(get("/api/path/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value(99))
                .andExpect(jsonPath("$.data[0].target").value("Redis"))
                .andExpect(jsonPath("$.data[0].status").value("in_progress"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/path/list"));
    }
}
