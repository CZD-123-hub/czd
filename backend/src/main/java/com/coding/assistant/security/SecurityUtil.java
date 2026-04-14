package com.coding.assistant.security;

import com.coding.assistant.exception.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtil {

    private SecurityUtil() {
        // utility class
    }

    public static Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtUserDetails userDetails)) {
            throw new BusinessException(401, "Not authenticated");
        }
        return userDetails.getUserId();
    }

    public static String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtUserDetails userDetails)) {
            throw new BusinessException(401, "Not authenticated");
        }
        return userDetails.getUsername();
    }
}