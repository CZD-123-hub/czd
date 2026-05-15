package com.coding.assistant.controller;

import com.coding.assistant.dto.ImportResultVO;
import com.coding.assistant.dto.PageResult;
import com.coding.assistant.dto.SnippetQueryRequest;
import com.coding.assistant.dto.SnippetVO;
import com.coding.assistant.exception.GlobalExceptionHandler;
import com.coding.assistant.security.JwtUserDetails;
import com.coding.assistant.service.ProgressService;
import com.coding.assistant.service.SnippetService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.nio.charset.StandardCharsets;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SnippetControllerContractTest {

    @Mock
    private SnippetService snippetService;

    @Mock
    private ProgressService progressService;

    @InjectMocks
    private SnippetController snippetController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        this.mockMvc = MockMvcBuilders.standaloneSetup(snippetController)
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
    void list_shouldBindLanguageFilterAndReturnPageResultContract() throws Exception {
        PageResult<SnippetVO> pageResult = PageResult.<SnippetVO>builder()
                .records(List.of(
                        SnippetVO.builder()
                                .id(11L)
                                .title("Redis lock")
                                .code("SETNX lock")
                                .language("java")
                                .description("distributed lock")
                                .tags(List.of("redis", "lock"))
                                .useCount(3)
                                .createdAt(LocalDateTime.of(2026, 4, 20, 9, 0))
                                .updatedAt(LocalDateTime.of(2026, 4, 20, 10, 0))
                                .build()
                ))
                .total(1L)
                .page(1)
                .size(10)
                .build();
        when(snippetService.list(eq(1L), any(SnippetQueryRequest.class))).thenReturn(pageResult);

        mockMvc.perform(get("/api/snippets")
                        .param("page", "1")
                        .param("size", "10")
                        .param("keyword", "redis")
                        .param("tag", "lock")
                        .param("language", "java"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.records[0].id").value(11))
                .andExpect(jsonPath("$.data.records[0].language").value("java"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/snippets"));

        ArgumentCaptor<SnippetQueryRequest> queryCaptor = ArgumentCaptor.forClass(SnippetQueryRequest.class);
        verify(snippetService).list(eq(1L), queryCaptor.capture());
        SnippetQueryRequest captured = queryCaptor.getValue();
        assertEquals("java", captured.getLanguage());
        assertEquals("redis", captured.getKeyword());
        assertEquals("lock", captured.getTag());
    }

    @Test
    void importSnippets_shouldReturnImportResultContract() throws Exception {
        ImportResultVO importResult = ImportResultVO.builder()
                .successCount(2)
                .failCount(1)
                .errors(List.of("Item 3: title is required"))
                .build();
        when(snippetService.importFromJson(eq(1L), anyString())).thenReturn(importResult);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "snippets.json",
                MediaType.APPLICATION_JSON_VALUE,
                "[]".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/snippets/import").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.successCount").value(2))
                .andExpect(jsonPath("$.data.failCount").value(1))
                .andExpect(jsonPath("$.data.errors[0]").value("Item 3: title is required"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/snippets/import"));

        verify(snippetService).importFromJson(1L, "[]");
    }

    @Test
    void list_shouldReturnValidationErrorContract_whenPageIsZero() throws Exception {
        mockMvc.perform(get("/api/snippets")
                        .param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.error.bizCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message", containsString("page")))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/snippets"));

        verify(snippetService, never()).list(anyLong(), any(SnippetQueryRequest.class));
    }
}
