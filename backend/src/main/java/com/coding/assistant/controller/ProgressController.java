package com.coding.assistant.controller;

import com.coding.assistant.dto.*;
import com.coding.assistant.security.SecurityUtil;
import com.coding.assistant.service.ProgressService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/progress")
@RequiredArgsConstructor
public class ProgressController {

    private final ProgressService progressService;

    @GetMapping("/dashboard")
    public ApiResponse<DashboardVO> getDashboard() {
        Long userId = SecurityUtil.getCurrentUserId();
        return ApiResponse.success(progressService.getDashboard(userId));
    }

    @GetMapping("/smart-insights")
    public ApiResponse<SmartInsightsVO> getSmartInsights() {
        Long userId = SecurityUtil.getCurrentUserId();
        return ApiResponse.success(progressService.getSmartInsights(userId));
    }

    @PostMapping("/weekly-plan/toggle")
    public ApiResponse<Void> toggleWeeklyPlan(@RequestBody WeeklyPlanToggleRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        if (request == null || request.getPlanId() == null || request.getCompleted() == null) {
            return ApiResponse.error(400, "参数不完整");
        }
        progressService.toggleWeeklyPlan(userId, request.getPlanId(), request.getCompleted());
        return ApiResponse.success();
    }

    @GetMapping("/heatmap")
    public ApiResponse<HeatmapVO> getHeatmap(@RequestParam(defaultValue = "0") int year) {
        Long userId = SecurityUtil.getCurrentUserId();
        if (year == 0) year = LocalDate.now().getYear();
        return ApiResponse.success(progressService.getHeatmap(userId, year));
    }

    @GetMapping("/radar")
    public ApiResponse<RadarVO> getRadar() {
        Long userId = SecurityUtil.getCurrentUserId();
        return ApiResponse.success(progressService.getRadar(userId));
    }

    @GetMapping("/report")
    public ResponseEntity<byte[]> generateReport() {
        Long userId = SecurityUtil.getCurrentUserId();
        byte[] pdfBytes = progressService.generateReport(userId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "learning-progress-report.pdf");
        headers.setContentLength(pdfBytes.length);
        return ResponseEntity.ok().headers(headers).body(pdfBytes);
    }
}
