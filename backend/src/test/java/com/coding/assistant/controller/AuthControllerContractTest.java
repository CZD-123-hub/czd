package com.coding.assistant.controller;

import com.coding.assistant.dto.RegisterRequest;
import com.coding.assistant.dto.TokenVO;
import com.coding.assistant.dto.UserVO;
import com.coding.assistant.exception.GlobalExceptionHandler;
import com.coding.assistant.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerContractTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        this.mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void register_shouldReturnTokenAndUserContract() throws Exception {
        TokenVO tokenVO = TokenVO.builder()
                .token("jwt-token")
                .user(UserVO.builder()
                        .id(1L)
                        .username("alice")
                        .email("alice@example.com")
                        .avatar("/uploads/avatars/a1.png")
                        .level("beginner")
                        .createdAt(LocalDateTime.of(2026, 4, 20, 12, 0))
                        .build())
                .build();
        when(authService.register(any(RegisterRequest.class))).thenReturn(tokenVO);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "alice",
                                  "password": "12345678",
                                  "email": "alice@example.com"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.token").value("jwt-token"))
                .andExpect(jsonPath("$.data.user.id").value(1))
                .andExpect(jsonPath("$.data.user.username").value("alice"))
                .andExpect(jsonPath("$.data.user.email").value("alice@example.com"))
                .andExpect(jsonPath("$.data.user.level").value("beginner"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/auth/register"));

        verify(authService).register(any(RegisterRequest.class));
    }

    @Test
    void register_shouldReturnValidationErrorContract_whenEmailMissing() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "alice",
                                  "password": "12345678"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.error.bizCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message", containsString("email")))
                .andExpect(jsonPath("$.error.details").isArray())
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/auth/register"));

        verifyNoInteractions(authService);
    }

    @Test
    void getCurrentUserInfo_shouldReturnUserContract() throws Exception {
        UserVO userVO = UserVO.builder()
                .id(2L)
                .username("bob")
                .email("bob@example.com")
                .avatar("/uploads/avatars/b2.png")
                .level("intermediate")
                .createdAt(LocalDateTime.of(2026, 4, 20, 12, 30))
                .build();
        when(authService.getCurrentUserInfo()).thenReturn(userVO);

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.id").value(2))
                .andExpect(jsonPath("$.data.username").value("bob"))
                .andExpect(jsonPath("$.data.email").value("bob@example.com"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/auth/me"));

        verify(authService).getCurrentUserInfo();
    }
}

