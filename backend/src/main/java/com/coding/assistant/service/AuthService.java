package com.coding.assistant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.coding.assistant.dto.LoginRequest;
import com.coding.assistant.dto.RegisterRequest;
import com.coding.assistant.dto.TokenVO;
import com.coding.assistant.dto.UserProfileVO;
import com.coding.assistant.dto.UserVO;
import com.coding.assistant.entity.User;
import com.coding.assistant.exception.BusinessException;
import com.coding.assistant.exception.ErrorCode;
import com.coding.assistant.mapper.UserMapper;
import com.coding.assistant.security.JwtUtil;
import com.coding.assistant.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/gif", "image/webp");

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final UserProfileService userProfileService;

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    @Transactional
    public TokenVO register(RegisterRequest request) {
        Long usernameCount = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername())
        );
        if (usernameCount > 0) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    ErrorCode.BIZ_USERNAME_EXISTS,
                    "Username already exists"
            );
        }

        Long emailCount = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getEmail, request.getEmail())
        );
        if (emailCount > 0) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    ErrorCode.BIZ_EMAIL_EXISTS,
                    "Email already registered"
            );
        }

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .level("beginner")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        userMapper.insert(user);

        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        log.info("User registered and logged in: {}", user.getUsername());
        return TokenVO.builder()
                .token(token)
                .user(toUserVO(user))
                .build();
    }

    public TokenVO login(LoginRequest request) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername())
        );
        if (user == null) {
            throw new BusinessException(
                    ErrorCode.UNAUTHORIZED,
                    ErrorCode.BIZ_INVALID_CREDENTIALS,
                    "Invalid username or password"
            );
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(
                    ErrorCode.UNAUTHORIZED,
                    ErrorCode.BIZ_INVALID_CREDENTIALS,
                    "Invalid username or password"
            );
        }

        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        log.info("User logged in: {}", user.getUsername());

        return TokenVO.builder()
                .token(token)
                .user(toUserVO(user))
                .build();
    }

    public UserVO uploadAvatar(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    ErrorCode.BIZ_INVALID_FILE,
                    "请选择要上传的文件"
            );
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    ErrorCode.BIZ_INVALID_FILE,
                    "仅支持 JPG、PNG、GIF、WebP 格式图片"
            );
        }

        if (file.getSize() > 5 * 1024 * 1024) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    ErrorCode.BIZ_INVALID_FILE,
                    "图片大小不能超过 5MB"
            );
        }

        Long userId = SecurityUtil.getCurrentUserId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND,
                    ErrorCode.BIZ_USER_NOT_FOUND,
                    "用户不存在"
            );
        }

        try {
            Path avatarDir = Paths.get(uploadDir, "avatars").toAbsolutePath().normalize();
            Files.createDirectories(avatarDir);

            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String filename = userId + "_" + UUID.randomUUID().toString().substring(0, 8) + extension;

            Path targetPath = avatarDir.resolve(filename);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            String avatarUrl = "/uploads/avatars/" + filename;
            user.setAvatar(avatarUrl);
            user.setUpdatedAt(LocalDateTime.now());
            userMapper.updateById(user);

            log.info("User {} uploaded avatar: {}", user.getUsername(), avatarUrl);
            return toUserVO(user);
        } catch (IOException e) {
            log.error("Failed to upload avatar", e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_SERVER_ERROR,
                    ErrorCode.BIZ_FILE_UPLOAD_FAILED,
                    "头像上传失败，请重试"
            );
        }
    }

    public Long getCurrentUserId() {
        return SecurityUtil.getCurrentUserId();
    }

    public UserVO getCurrentUserInfo() {
        Long userId = SecurityUtil.getCurrentUserId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND,
                    ErrorCode.BIZ_USER_NOT_FOUND,
                    "用户不存在"
            );
        }
        return toUserVO(user);
    }

    public UserProfileVO getProfileSummary() {
        Long userId = SecurityUtil.getCurrentUserId();
        return userProfileService.buildProfile(userId);
    }

    private UserVO toUserVO(User user) {
        return UserVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .avatar(user.getAvatar())
                .level(user.getLevel())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
