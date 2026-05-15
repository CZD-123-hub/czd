package com.coding.assistant.controller;

import com.coding.assistant.dto.ApiResponse;
import com.coding.assistant.dto.ExamGenerateRequest;
import com.coding.assistant.dto.ExamResultVO;
import com.coding.assistant.dto.ExamSubmitRequest;
import com.coding.assistant.dto.PracticeAnswerRequest;
import com.coding.assistant.dto.PracticeAnswerResultVO;
import com.coding.assistant.dto.PracticeGenerateRequest;
import com.coding.assistant.dto.PracticeSessionVO;
import com.coding.assistant.security.SecurityUtil;
import com.coding.assistant.service.PracticeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/practice")
@RequiredArgsConstructor
public class PracticeController {

    private final PracticeService practiceService;

    @PostMapping("/sessions/generate")
    public ApiResponse<PracticeSessionVO> generate(@Valid @RequestBody PracticeGenerateRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        return ApiResponse.success(practiceService.generatePractice(userId, request));
    }

    @GetMapping("/sessions/latest")
    public ApiResponse<PracticeSessionVO> latest() {
        Long userId = SecurityUtil.getCurrentUserId();
        return ApiResponse.success(practiceService.getLatestPractice(userId));
    }

    @PostMapping("/exams/generate")
    public ApiResponse<PracticeSessionVO> generateExam(@Valid @RequestBody ExamGenerateRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        return ApiResponse.success(practiceService.generateExam(userId, request));
    }

    @GetMapping("/exams/latest")
    public ApiResponse<PracticeSessionVO> latestExam() {
        Long userId = SecurityUtil.getCurrentUserId();
        return ApiResponse.success(practiceService.getLatestExam(userId));
    }

    @GetMapping("/sessions/{sessionId}")
    public ApiResponse<PracticeSessionVO> detail(@PathVariable Long sessionId) {
        Long userId = SecurityUtil.getCurrentUserId();
        return ApiResponse.success(practiceService.getSession(userId, sessionId));
    }

    @PostMapping("/sessions/{sessionId}/answer")
    public ApiResponse<PracticeAnswerResultVO> answer(
            @PathVariable Long sessionId,
            @Valid @RequestBody PracticeAnswerRequest request
    ) {
        Long userId = SecurityUtil.getCurrentUserId();
        return ApiResponse.success(practiceService.answerPracticeQuestion(userId, sessionId, request));
    }

    @PostMapping("/exams/{sessionId}/submit")
    public ApiResponse<ExamResultVO> submitExam(
            @PathVariable Long sessionId,
            @Valid @RequestBody ExamSubmitRequest request
    ) {
        Long userId = SecurityUtil.getCurrentUserId();
        return ApiResponse.success(practiceService.submitExam(userId, sessionId, request));
    }
}
