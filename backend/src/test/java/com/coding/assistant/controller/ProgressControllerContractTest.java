package com.coding.assistant.controller;

import com.coding.assistant.dto.ActivityVO;
import com.coding.assistant.dto.CoverageDetailVO;
import com.coding.assistant.dto.DashboardComparisonsVO;
import com.coding.assistant.dto.DashboardVO;
import com.coding.assistant.dto.MetricComparisonVO;
import com.coding.assistant.exception.GlobalExceptionHandler;
import com.coding.assistant.security.JwtUserDetails;
import com.coding.assistant.service.ProgressService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProgressControllerContractTest {

    @Mock
    private ProgressService progressService;

    @InjectMocks
    private ProgressController progressController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        this.mockMvc = MockMvcBuilders.standaloneSetup(progressController)
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
    void getDashboard_shouldReturnNestedDashboardContract() throws Exception {
        DashboardVO dashboard = DashboardVO.builder()
                .totalDays(12)
                .totalChats(30)
                .totalSnippets(8)
                .knowledgeCoverage(60.0)
                .periodDays(30)
                .comparisons(DashboardComparisonsVO.builder()
                        .totalDays(MetricComparisonVO.builder().current(12).previous(9).delta(3).trend("up").build())
                        .totalChats(MetricComparisonVO.builder().current(30).previous(20).delta(10).trend("up").build())
                        .totalSnippets(MetricComparisonVO.builder().current(8).previous(6).delta(2).trend("up").build())
                        .knowledgeCoverage(MetricComparisonVO.builder().current(60).previous(40).delta(20).trend("up").build())
                        .build())
                .coverageDetail(CoverageDetailVO.builder()
                        .coveredCoreActions(3)
                        .totalCoreActions(5)
                        .coveredActionKeys(List.of("chat", "code_save", "feedback"))
                        .build())
                .recentActivity(List.of(ActivityVO.builder().date("2026-04-20").count(4).build()))
                .build();
        when(progressService.getDashboard(1L)).thenReturn(dashboard);

        mockMvc.perform(get("/api/progress/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.totalDays").value(12))
                .andExpect(jsonPath("$.data.totalChats").value(30))
                .andExpect(jsonPath("$.data.totalSnippets").value(8))
                .andExpect(jsonPath("$.data.knowledgeCoverage").value(60.0))
                .andExpect(jsonPath("$.data.periodDays").value(30))
                .andExpect(jsonPath("$.data.comparisons.totalChats.current").value(30.0))
                .andExpect(jsonPath("$.data.coverageDetail.coveredCoreActions").value(3))
                .andExpect(jsonPath("$.data.recentActivity[0].date").value("2026-04-20"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/progress/dashboard"));

        verify(progressService).getDashboard(1L);
    }

    @Test
    void toggleWeeklyPlan_shouldReturnValidationErrorContract_whenCompletedMissing() throws Exception {
        mockMvc.perform(post("/api/progress/weekly-plan/toggle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "planId": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.error.bizCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message", containsString("required")))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/progress/weekly-plan/toggle"));

        verify(progressService, never()).toggleWeeklyPlan(anyLong(), anyString(), anyBoolean());
    }

    @Test
    void generateReport_shouldReturnPdfContract() throws Exception {
        byte[] pdfBytes = new byte[]{1, 2, 3, 4};
        when(progressService.generateReport(1L)).thenReturn(pdfBytes);

        mockMvc.perform(get("/api/progress/report"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, pdfBytes.length))
                .andExpect(content().bytes(pdfBytes));
    }
}
