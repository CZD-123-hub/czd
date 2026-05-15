package com.coding.assistant.controller;

import com.coding.assistant.dto.EdgeVO;
import com.coding.assistant.dto.GraphVO;
import com.coding.assistant.dto.KnowledgeNodeVO;
import com.coding.assistant.exception.BusinessException;
import com.coding.assistant.exception.ErrorCode;
import com.coding.assistant.exception.GlobalExceptionHandler;
import com.coding.assistant.security.JwtUserDetails;
import com.coding.assistant.service.GraphService;
import com.coding.assistant.service.LearningActionType;
import com.coding.assistant.service.ProgressService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class GraphControllerContractTest {

    @Mock
    private GraphService graphService;

    @Mock
    private ProgressService progressService;

    @InjectMocks
    private GraphController graphController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        this.mockMvc = MockMvcBuilders.standaloneSetup(graphController)
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
    void getOverview_shouldReturnGraphContract() throws Exception {
        GraphVO graph = GraphVO.builder()
                .nodes(List.of(
                        KnowledgeNodeVO.builder()
                                .id("java")
                                .name("Java")
                                .category("language")
                                .description("JVM language")
                                .difficulty("medium")
                                .keywords(List.of("jvm", "oop"))
                                .build()
                ))
                .edges(List.of(EdgeVO.builder().source("java").target("spring-boot").type("DEPENDS_ON").build()))
                .build();
        when(graphService.getOverview()).thenReturn(graph);

        mockMvc.perform(get("/api/graph/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.nodes[0].id").value("java"))
                .andExpect(jsonPath("$.data.nodes[0].name").value("Java"))
                .andExpect(jsonPath("$.data.edges[0].type").value("DEPENDS_ON"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/graph/overview"));

        verify(graphService).getOverview();
        verify(progressService).recordAction(1L, LearningActionType.GRAPH_EXPLORE, "overview");
    }

    @Test
    void getNeighbors_shouldBindDepthAndReturnGraphContract() throws Exception {
        GraphVO graph = GraphVO.builder()
                .nodes(List.of(
                        KnowledgeNodeVO.builder().id("java").name("Java").build(),
                        KnowledgeNodeVO.builder().id("spring-boot").name("Spring Boot").build()
                ))
                .edges(List.of(EdgeVO.builder().source("java").target("spring-boot").type("DEPENDS_ON").build()))
                .build();
        when(graphService.getNeighbors("java", 2)).thenReturn(graph);

        mockMvc.perform(get("/api/graph/node/java/neighbors").param("depth", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.nodes[1].id").value("spring-boot"))
                .andExpect(jsonPath("$.data.edges[0].source").value("java"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/graph/node/java/neighbors"));

        verify(graphService).getNeighbors("java", 2);
        verify(progressService).recordAction(1L, LearningActionType.GRAPH_EXPLORE, "java");
    }

    @Test
    void getNodeDetail_shouldReturnBusinessErrorContract_whenNodeNotFound() throws Exception {
        when(graphService.getNodeDetail("missing-node")).thenThrow(
                new BusinessException(ErrorCode.NOT_FOUND, ErrorCode.BIZ_NOT_FOUND, "Node not found")
        );

        mockMvc.perform(get("/api/graph/node/missing-node"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.error.bizCode").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message", containsString("Node not found")))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/graph/node/missing-node"));
    }

    @Test
    void search_shouldReturnGraphContract() throws Exception {
        GraphVO graph = GraphVO.builder()
                .nodes(List.of(KnowledgeNodeVO.builder().id("redis").name("Redis").category("database").build()))
                .edges(List.of())
                .build();
        when(graphService.searchGraph("redis")).thenReturn(graph);

        mockMvc.perform(get("/api/graph/search").param("keyword", "redis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.nodes[0].id").value("redis"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/graph/search"));

        verify(graphService).searchGraph("redis");
    }
}
