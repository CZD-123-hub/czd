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
    public ApiResponse<UserVO> register(@Valid @RequestBody RegisterRequest request) {
        UserVO user = authService.register(request);
        return ApiResponse.success(user);
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

    @PostMapping("/avatar")
    public ApiResponse<UserVO> uploadAvatar(@RequestParam("file") MultipartFile file) {
        UserVO user = authService.uploadAvatar(file);
        return ApiResponse.success(user);
    }
}