package com.coding.assistant.exception;

public final class ErrorCode {

    private ErrorCode() {}

    public static final int SUCCESS = 200;
    public static final int BAD_REQUEST = 400;
    public static final int UNAUTHORIZED = 401;
    public static final int FORBIDDEN = 403;
    public static final int NOT_FOUND = 404;
    public static final int TOO_MANY_REQUESTS = 429;
    public static final int INTERNAL_SERVER_ERROR = 500;

    public static final String BIZ_SUCCESS = "SUCCESS";
    public static final String BIZ_BAD_REQUEST = "BAD_REQUEST";
    public static final String BIZ_VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String BIZ_UNAUTHORIZED = "UNAUTHORIZED";
    public static final String BIZ_FORBIDDEN = "FORBIDDEN";
    public static final String BIZ_NOT_FOUND = "NOT_FOUND";
    public static final String BIZ_TOO_MANY_REQUESTS = "TOO_MANY_REQUESTS";
    public static final String BIZ_INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR";
    public static final String BIZ_CONFLICT = "CONFLICT";

    public static final String BIZ_USERNAME_EXISTS = "USERNAME_EXISTS";
    public static final String BIZ_EMAIL_EXISTS = "EMAIL_EXISTS";
    public static final String BIZ_INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
    public static final String BIZ_INVALID_FILE = "INVALID_FILE";
    public static final String BIZ_FILE_UPLOAD_FAILED = "FILE_UPLOAD_FAILED";
    public static final String BIZ_USER_NOT_FOUND = "USER_NOT_FOUND";
    public static final String BIZ_CONVERSATION_NOT_FOUND = "CONVERSATION_NOT_FOUND";
    public static final String BIZ_SNIPPET_NOT_FOUND = "SNIPPET_NOT_FOUND";
    public static final String BIZ_MESSAGE_NOT_FOUND = "MESSAGE_NOT_FOUND";
    public static final String BIZ_DOCUMENT_NOT_FOUND = "DOCUMENT_NOT_FOUND";
    public static final String BIZ_LEARNING_PATH_NOT_FOUND = "LEARNING_PATH_NOT_FOUND";
    public static final String BIZ_LEARNING_NODE_NOT_FOUND = "LEARNING_NODE_NOT_FOUND";
    public static final String BIZ_AI_PROVIDER_UNAVAILABLE = "AI_PROVIDER_UNAVAILABLE";
    public static final String BIZ_AI_GENERATION_FAILED = "AI_GENERATION_FAILED";
    public static final String BIZ_EXPORT_FAILED = "EXPORT_FAILED";

    public static String defaultBizCode(int statusCode) {
        return switch (statusCode) {
            case SUCCESS -> BIZ_SUCCESS;
            case BAD_REQUEST -> BIZ_BAD_REQUEST;
            case UNAUTHORIZED -> BIZ_UNAUTHORIZED;
            case FORBIDDEN -> BIZ_FORBIDDEN;
            case NOT_FOUND -> BIZ_NOT_FOUND;
            case TOO_MANY_REQUESTS -> BIZ_TOO_MANY_REQUESTS;
            default -> BIZ_INTERNAL_SERVER_ERROR;
        };
    }

    public static String defaultMessage(int statusCode) {
        return switch (statusCode) {
            case SUCCESS -> "success";
            case BAD_REQUEST -> "Bad request";
            case UNAUTHORIZED -> "Unauthorized";
            case FORBIDDEN -> "Forbidden";
            case NOT_FOUND -> "Resource not found";
            case TOO_MANY_REQUESTS -> "Too many requests";
            default -> "Internal server error";
        };
    }
}
