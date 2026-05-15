package com.coding.assistant.controller;

import com.coding.assistant.dto.*;
import com.coding.assistant.security.SecurityUtil;
import com.coding.assistant.service.ProgressService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
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
    public ApiResponse<Void> toggleWeeklyPlan(@Valid @RequestBody WeeklyPlanToggleRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
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
        headers.setContentDisposition(
                ContentDisposition.attachment().filename("学习数据报告.pdf", StandardCharsets.UTF_8).build()
        );
        headers.setCacheControl("no-store, no-cache, must-revalidate, max-age=0");
        headers.add("Pragma", "no-cache");
        headers.add("Expires", "0");
        headers.setContentLength(pdfBytes.length);
        return ResponseEntity.ok().headers(headers).body(pdfBytes);
    }
}
