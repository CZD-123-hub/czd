package com.coding.assistant.dto;

import com.coding.assistant.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    private int code;
    private String message;
    private T data;
    private ApiError error;
    private String traceId;
    private String timestamp;
    private String path;

    public static <T> ApiResponse<T> success(T data) {
        ResponseMeta meta = resolveMeta();
        return ApiResponse.<T>builder()
                .code(ErrorCode.SUCCESS)
                .message("success")
                .data(data)
                .traceId(meta.traceId())
                .timestamp(meta.timestamp())
                .path(meta.path())
                .build();
    }

    public static <T> ApiResponse<T> success() {
        ResponseMeta meta = resolveMeta();
        return ApiResponse.<T>builder()
                .code(ErrorCode.SUCCESS)
                .message("success")
                .traceId(meta.traceId())
                .timestamp(meta.timestamp())
                .path(meta.path())
                .build();
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return error(code, ErrorCode.defaultBizCode(code), message, null);
    }

    public static <T> ApiResponse<T> error(int code, String bizCode, String message, Object details) {
        ResponseMeta meta = resolveMeta();
        return ApiResponse.<T>builder()
                .code(code)
                .message(message)
                .error(ApiError.builder()
                        .bizCode(bizCode)
                        .details(details)
                        .build())
                .traceId(meta.traceId())
                .timestamp(meta.timestamp())
                .path(meta.path())
                .build();
    }

    private static ResponseMeta resolveMeta() {
        String timestamp = Instant.now().toString();
        String traceId = null;
        String path = null;

        var attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            HttpServletRequest request = servletAttributes.getRequest();
            Object traceIdAttr = request.getAttribute("traceId");
            if (traceIdAttr != null) {
                traceId = String.valueOf(traceIdAttr);
            }
            path = request.getRequestURI();
        }

        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }

        return new ResponseMeta(traceId, timestamp, path);
    }

    private record ResponseMeta(String traceId, String timestamp, String path) {}
}
