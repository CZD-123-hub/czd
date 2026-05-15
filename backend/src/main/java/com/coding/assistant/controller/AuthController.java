package com.coding.assistant.controller;

import com.coding.assistant.dto.*;
import com.coding.assistant.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ApiResponse<TokenVO> register(@Valid @RequestBody RegisterRequest request) {
        TokenVO token = authService.register(request);
        return ApiResponse.success(token);
    }

    @PostMapping("/login")
    public ApiResponse<TokenVO> login(@Valid @RequestBody LoginRequest request) {
        TokenVO token = authService.login(request);
        return ApiResponse.success(token);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        // Stateless JWT - client discards token
        return ApiResponse.success();
    }

    @GetMapping("/me")
    public ApiResponse<UserVO> getCurrentUserInfo() {
        return ApiResponse.success(authService.getCurrentUserInfo());
    }

    @GetMapping("/profile-summary")
    public ApiResponse<UserProfileVO> getProfileSummary() {
        return ApiResponse.success(authService.getProfileSummary());
    }

    @PostMapping("/avatar")
    public ApiResponse<UserVO> uploadAvatar(@RequestParam("file") MultipartFile file) {
        UserVO user = authService.uploadAvatar(file);
        return ApiResponse.success(user);
    }
}
