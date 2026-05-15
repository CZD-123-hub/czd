package com.coding.assistant.security;

import com.coding.assistant.exception.BusinessException;
import com.coding.assistant.exception.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtil {

    private SecurityUtil() {
        // utility class
    }

    public static Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtUserDetails userDetails)) {
            throw new BusinessException(
                    ErrorCode.UNAUTHORIZED,
                    ErrorCode.BIZ_UNAUTHORIZED,
                    "Not authenticated"
            );
        }
        Long userId = userDetails.getUserId();
        if (userId == null || userId <= 0) {
            throw new BusinessException(
                    ErrorCode.UNAUTHORIZED,
                    ErrorCode.BIZ_UNAUTHORIZED,
                    "Invalid authentication token. Please login again."
            );
        }
        return userId;
    }

    public static String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtUserDetails userDetails)) {
            throw new BusinessException(
                    ErrorCode.UNAUTHORIZED,
                    ErrorCode.BIZ_UNAUTHORIZED,
                    "Not authenticated"
            );
        }
        String username = userDetails.getUsername();
        if (username == null || username.isBlank()) {
            throw new BusinessException(
                    ErrorCode.UNAUTHORIZED,
                    ErrorCode.BIZ_UNAUTHORIZED,
                    "Invalid authentication token. Please login again."
            );
        }
        return username;
    }
}
