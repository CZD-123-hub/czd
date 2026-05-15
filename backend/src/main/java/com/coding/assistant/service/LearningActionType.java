package com.coding.assistant.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class LearningActionType {

    public static final String CHAT = "chat";
    public static final String CODE_SAVE = "code_save";
    public static final String PATH_LEARN = "path_learn";
    public static final String GRAPH_EXPLORE = "graph_explore";
    public static final String FEEDBACK = "feedback";
    public static final String VIDEO_WATCH = "video_watch";
    public static final String VIDEO_FAVORITE = "video_favorite";
    public static final String PRACTICE_START = "practice_start";
    public static final String PRACTICE_COMPLETE = "practice_complete";
    public static final String EXAM_COMPLETE = "exam_complete";
    public static final String WEEKLY_PLAN_DONE = "weekly_plan_done";

    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry(CHAT, CHAT),
            Map.entry("chat_message", CHAT),
            Map.entry("conversation_chat", CHAT),
            Map.entry(CODE_SAVE, CODE_SAVE),
            Map.entry("code", CODE_SAVE),
            Map.entry("snippet_save", CODE_SAVE),
            Map.entry("code_snippet_save", CODE_SAVE),
            Map.entry(PATH_LEARN, PATH_LEARN),
            Map.entry("path_progress", PATH_LEARN),
            Map.entry("path_update", PATH_LEARN),
            Map.entry(GRAPH_EXPLORE, GRAPH_EXPLORE),
            Map.entry("graph", GRAPH_EXPLORE),
            Map.entry("graph_view", GRAPH_EXPLORE),
            Map.entry(FEEDBACK, FEEDBACK),
            Map.entry("feedback_submit", FEEDBACK),
            Map.entry("chat_feedback", FEEDBACK),
            Map.entry(VIDEO_WATCH, VIDEO_WATCH),
            Map.entry("watch_video", VIDEO_WATCH),
            Map.entry("video_play", VIDEO_WATCH),
            Map.entry(VIDEO_FAVORITE, VIDEO_FAVORITE),
            Map.entry("favorite_video", VIDEO_FAVORITE),
            Map.entry(PRACTICE_START, PRACTICE_START),
            Map.entry("practice", PRACTICE_START),
            Map.entry(PRACTICE_COMPLETE, PRACTICE_COMPLETE),
            Map.entry("practice_done", PRACTICE_COMPLETE),
            Map.entry(EXAM_COMPLETE, EXAM_COMPLETE),
            Map.entry("exam", EXAM_COMPLETE),
            Map.entry(WEEKLY_PLAN_DONE, WEEKLY_PLAN_DONE)
    );

    private static final Set<String> CORE_ACTIONS = Set.of(CHAT, CODE_SAVE, PATH_LEARN, GRAPH_EXPLORE, FEEDBACK);

    private LearningActionType() {
    }

    public static String normalize(String actionType) {
        if (actionType == null) {
            return "";
        }
        String key = actionType.trim().toLowerCase(Locale.ROOT);
        if (key.isEmpty()) {
            return "";
        }
        return ALIASES.getOrDefault(key, key);
    }

    public static List<String> radarKeys() {
        return List.of(CHAT, CODE_SAVE, PATH_LEARN, GRAPH_EXPLORE, FEEDBACK);
    }

    public static String radarLabel(String actionType) {
        return switch (actionType) {
            case CHAT -> "Chat";
            case CODE_SAVE -> "Code Snippets";
            case PATH_LEARN -> "Learning Paths";
            case GRAPH_EXPLORE -> "Graph Exploration";
            case FEEDBACK -> "Feedback";
            default -> actionType;
        };
    }

    public static boolean isCoreAction(String actionType) {
        return CORE_ACTIONS.contains(actionType);
    }
}
