package com.coding.assistant.controller;

import com.coding.assistant.dto.KnowledgeDocumentVO;
import com.coding.assistant.entity.KnowledgeDocument;
import com.coding.assistant.exception.GlobalExceptionHandler;
import com.coding.assistant.security.JwtUserDetails;
import com.coding.assistant.service.DocumentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DocumentControllerContractTest {

    @Mock
    private DocumentService documentService;

    @InjectMocks
    private DocumentController documentController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        JwtUserDetails user = new JwtUserDetails(4L, "czd");
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        this.mockMvc = MockMvcBuilders.standaloneSetup(documentController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void list_shouldReturnDocumentArrayContract() throws Exception {
        KnowledgeDocumentVO doc = KnowledgeDocumentVO.builder()
                .id(10L)
                .title("Spring Boot Redis")
                .content("Cache with redis")
                .category("backend")
                .createdAt(LocalDateTime.of(2026, 4, 20, 18, 0))
                .build();
        when(documentService.listAll(anyLong(), anyBoolean())).thenReturn(List.of(doc));

        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data[0].id").value(10))
                .andExpect(jsonPath("$.data[0].title").value("Spring Boot Redis"))
                .andExpect(jsonPath("$.data[0].content").value("Cache with redis"))
                .andExpect(jsonPath("$.data[0].category").value("backend"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/documents"));

        verify(documentService).listAll(4L, false);
    }

    @Test
    void add_shouldReturnDocumentContract() throws Exception {
        KnowledgeDocument created = KnowledgeDocument.builder()
                .id(11L)
                .title("MySQL Index")
                .content("Use composite index")
                .category("database")
                .createdAt(LocalDateTime.of(2026, 4, 20, 18, 30))
                .build();
        when(documentService.addDocument("MySQL Index", "Use composite index", "database")).thenReturn(created);

        mockMvc.perform(post("/api/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "MySQL Index",
                                  "content": "Use composite index",
                                  "category": "database"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.id").value(11))
                .andExpect(jsonPath("$.data.title").value("MySQL Index"))
                .andExpect(jsonPath("$.data.content").value("Use composite index"))
                .andExpect(jsonPath("$.data.category").value("database"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/documents"));

        ArgumentCaptor<String> titleCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> categoryCaptor = ArgumentCaptor.forClass(String.class);
        verify(documentService).addDocument(titleCaptor.capture(), contentCaptor.capture(), categoryCaptor.capture());
        assertEquals("MySQL Index", titleCaptor.getValue());
        assertEquals("Use composite index", contentCaptor.getValue());
        assertEquals("database", categoryCaptor.getValue());
    }

    @Test
    void add_shouldReturnValidationErrorContract_whenContentMissing() throws Exception {
        mockMvc.perform(post("/api/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "MySQL Index",
                                  "category": "database"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.error.bizCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message", containsString("content")))
                .andExpect(jsonPath("$.error.details").isArray())
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/documents"));
    }

    @Test
    void delete_shouldReturnSuccessContract() throws Exception {
        mockMvc.perform(delete("/api/documents/21"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/documents/21"));

        verify(documentService).deleteDocument(anyLong());
    }
}
