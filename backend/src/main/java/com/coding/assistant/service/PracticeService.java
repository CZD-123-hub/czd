package com.coding.assistant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.coding.assistant.dto.ExamGenerateRequest;
import com.coding.assistant.dto.ExamResultVO;
import com.coding.assistant.dto.ExamSubmitRequest;
import com.coding.assistant.dto.PracticeAnswerRequest;
import com.coding.assistant.dto.PracticeAnswerResultVO;
import com.coding.assistant.dto.PracticeGenerateRequest;
import com.coding.assistant.dto.PracticeQuestionVO;
import com.coding.assistant.dto.PracticeSessionVO;
import com.coding.assistant.entity.LearningNode;
import com.coding.assistant.entity.LearningPath;
import com.coding.assistant.entity.PracticeQuestion;
import com.coding.assistant.entity.PracticeSession;
import com.coding.assistant.exception.BusinessException;
import com.coding.assistant.exception.ErrorCode;
import com.coding.assistant.mapper.LearningNodeMapper;
import com.coding.assistant.mapper.LearningPathMapper;
import com.coding.assistant.mapper.PracticeQuestionMapper;
import com.coding.assistant.mapper.PracticeSessionMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PracticeService {

    private static final String MODE_PRACTICE = "practice";
    private static final String MODE_EXAM = "exam";
    private static final String STATUS_ONGOING = "ongoing";
    private static final String STATUS_COMPLETED = "completed";
    private static final List<String> OPTION_KEYS = List.of("A", "B", "C", "D", "E", "F");
    private static final List<String> GENERIC_DISTRACTORS = List.of(
            "数据结构", "算法", "数据库", "计算机网络", "操作系统", "软件工程", "设计模式", "系统架构"
    );

    private final LearningPathMapper learningPathMapper;
    private final LearningNodeMapper learningNodeMapper;
    private final PracticeSessionMapper practiceSessionMapper;
    private final PracticeQuestionMapper practiceQuestionMapper;
    private final ProgressService progressService;
    private final ObjectMapper objectMapper;

    @Transactional
    public PracticeSessionVO generatePractice(Long userId, PracticeGenerateRequest request) {
        int questionCount = sanitizeCount(request.getQuestionCount(), 3, 20, 6);
        PracticeSession session = generateSession(
                userId,
                request.getPathId(),
                questionCount,
                MODE_PRACTICE,
                null,
                "以下哪个选项与当前学习节点最匹配？",
                true
        );
        return getSession(userId, session.getId());
    }

    @Transactional
    public PracticeSessionVO generateExam(Long userId, ExamGenerateRequest request) {
        int questionCount = sanitizeCount(request.getQuestionCount(), 5, 30, 10);
        int duration = sanitizeCount(request.getDurationMinutes(), 5, 180, 20);
        PracticeSession session = generateSession(
                userId,
                request.getPathId(),
                questionCount,
                MODE_EXAM,
                duration,
                "在给定知识主题下，以下哪个选项最符合要求？",
                false
        );
        return getSession(userId, session.getId());
    }

    public PracticeSessionVO getLatestPractice(Long userId) {
        PracticeSession session = practiceSessionMapper.selectLatestByUserIdAndMode(userId, MODE_PRACTICE);
        if (session == null) return null;
        return toSessionVO(session, practiceQuestionMapper.selectBySessionId(session.getId()));
    }

    public PracticeSessionVO getLatestExam(Long userId) {
        PracticeSession session = practiceSessionMapper.selectLatestByUserIdAndMode(userId, MODE_EXAM);
        if (session == null) return null;
        return toSessionVO(session, practiceQuestionMapper.selectBySessionId(session.getId()));
    }

    public PracticeSessionVO getSession(Long userId, Long sessionId) {
        PracticeSession session = loadSession(userId, sessionId);
        List<PracticeQuestion> questions = practiceQuestionMapper.selectBySessionId(sessionId);
        return toSessionVO(session, questions);
    }

    @Transactional
    public PracticeAnswerResultVO answerPracticeQuestion(Long userId, Long sessionId, PracticeAnswerRequest request) {
        PracticeSession session = loadSession(userId, sessionId);
        ensureMode(session, MODE_PRACTICE, "该会话不是练习模式");
        ensureOngoing(session, "练习已结束，请重新生成题目");

        PracticeQuestion question = loadQuestion(sessionId, request.getQuestionId());
        List<String> options = parseOptions(question.getOptionsJson());
        String normalizedAnswer = normalizeAnswer(request.getAnswer(), options);
        boolean correct = normalizedAnswer.equalsIgnoreCase(safe(question.getCorrectAnswer()));

        question.setUserAnswer(normalizedAnswer);
        question.setIsCorrect(correct ? 1 : 0);
        question.setUpdatedAt(LocalDateTime.now());
        practiceQuestionMapper.updateById(question);

        SessionStat stat = recalculateSessionStat(sessionId);
        session.setAnsweredCount(stat.answeredCount());
        session.setCorrectCount(stat.correctCount());
        session.setUpdatedAt(LocalDateTime.now());

        boolean completed = stat.answeredCount() >= safeInt(session.getTotalQuestions());
        if (completed) {
            session.setStatus(STATUS_COMPLETED);
            session.setSubmittedAt(LocalDateTime.now());
            progressService.recordAction(userId, LearningActionType.PRACTICE_COMPLETE, String.valueOf(session.getId()));
        }
        practiceSessionMapper.updateById(session);

        return PracticeAnswerResultVO.builder()
                .sessionId(sessionId)
                .questionId(question.getId())
                .answer(normalizedAnswer)
                .correctAnswer(question.getCorrectAnswer())
                .correct(correct)
                .explanation(question.getExplanation())
                .answeredCount(session.getAnsweredCount())
                .correctCount(session.getCorrectCount())
                .completed(completed)
                .build();
    }

    @Transactional
    public ExamResultVO submitExam(Long userId, Long sessionId, ExamSubmitRequest request) {
        PracticeSession session = loadSession(userId, sessionId);
        ensureMode(session, MODE_EXAM, "该会话不是测评模式");
        ensureOngoing(session, "测评已结束，请重新组卷");

        boolean timeout = isExamTimeout(session);
        Map<Long, String> submittedMap = new LinkedHashMap<>();
        for (ExamSubmitRequest.ExamAnswerItem item : request.getAnswers()) {
            submittedMap.put(item.getQuestionId(), item.getAnswer());
        }

        List<PracticeQuestion> questions = practiceQuestionMapper.selectBySessionId(sessionId);
        for (PracticeQuestion question : questions) {
            String rawAnswer = submittedMap.get(question.getId());
            if (rawAnswer == null) {
                continue;
            }
            List<String> options = parseOptions(question.getOptionsJson());
            String normalized = normalizeAnswer(rawAnswer, options);
            boolean correct = normalized.equalsIgnoreCase(safe(question.getCorrectAnswer()));
            question.setUserAnswer(normalized);
            question.setIsCorrect(correct ? 1 : 0);
            question.setUpdatedAt(LocalDateTime.now());
            practiceQuestionMapper.updateById(question);
        }

        SessionStat stat = recalculateSessionStat(sessionId);
        int total = Math.max(1, safeInt(session.getTotalQuestions()));
        double score = Math.round((stat.correctCount() * 10000.0 / total)) / 100.0;
        String grade = gradeByScore(score);

        session.setAnsweredCount(stat.answeredCount());
        session.setCorrectCount(stat.correctCount());
        session.setScore(score);
        session.setGrade(grade);
        session.setStatus(STATUS_COMPLETED);
        session.setSubmittedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        practiceSessionMapper.updateById(session);

        progressService.recordAction(userId, LearningActionType.EXAM_COMPLETE, String.valueOf(session.getId()));

        return ExamResultVO.builder()
                .sessionId(sessionId)
                .totalQuestions(total)
                .answeredCount(stat.answeredCount())
                .correctCount(stat.correctCount())
                .score(score)
                .grade(grade)
                .timeout(timeout)
                .build();
    }

    private PracticeSession generateSession(
            Long userId,
            Long pathId,
            int questionCount,
            String mode,
            Integer durationMinutes,
            String stem,
            boolean recordPracticeStart
    ) {
        LearningPath path = resolvePath(userId, pathId);
        List<LearningNode> nodes = learningNodeMapper.selectByPathId(path.getId());
        if (nodes == null || nodes.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    ErrorCode.BIZ_BAD_REQUEST,
                    "当前学习路径暂无可用节点，无法生成题目"
            );
        }

        List<LearningNode> selectedNodes = pickNodes(nodes, questionCount);
        List<String> allTopics = selectedNodes.stream()
                .map(this::nodeTopicName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .collect(Collectors.toList());

        LocalDateTime now = LocalDateTime.now();
        PracticeSession session = PracticeSession.builder()
                .userId(userId)
                .pathId(path.getId())
                .mode(mode)
                .status(STATUS_ONGOING)
                .totalQuestions(questionCount)
                .answeredCount(0)
                .correctCount(0)
                .durationMinutes(durationMinutes)
                .createdAt(now)
                .updatedAt(now)
                .build();
        practiceSessionMapper.insert(session);

        Random random = new Random();
        for (int i = 0; i < selectedNodes.size(); i++) {
            LearningNode node = selectedNodes.get(i);
            String topic = nodeTopicName(node);
            List<String> options = buildOptions(topic, allTopics, random);
            String correctAnswer = findCorrectAnswer(options, topic);

            PracticeQuestion question = PracticeQuestion.builder()
                    .sessionId(session.getId())
                    .knowledgeId(node.getKnowledgeId())
                    .questionStem(stem)
                    .optionsJson(toJson(options))
                    .correctAnswer(correctAnswer)
                    .explanation("本题来自学习路径节点「" + topic + "」，建议复习该节点推荐资料后再继续。")
                    .questionOrder(i + 1)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            practiceQuestionMapper.insert(question);
        }

        if (recordPracticeStart) {
            progressService.recordAction(userId, LearningActionType.PRACTICE_START, String.valueOf(session.getId()));
        }
        return session;
    }

    private LearningPath resolvePath(Long userId, Long pathId) {
        if (pathId != null) {
            LearningPath path = learningPathMapper.selectById(pathId);
            if (path == null || !userId.equals(path.getUserId())) {
                throw new BusinessException(
                        ErrorCode.NOT_FOUND,
                        ErrorCode.BIZ_LEARNING_PATH_NOT_FOUND,
                        "学习路径不存在"
                );
            }
            return path;
        }

        List<LearningPath> paths = learningPathMapper.selectList(
                new LambdaQueryWrapper<LearningPath>()
                        .eq(LearningPath::getUserId, userId)
                        .orderByDesc(LearningPath::getUpdatedAt)
                        .orderByDesc(LearningPath::getId)
                        .last("LIMIT 1")
        );
        if (paths == null || paths.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    ErrorCode.BIZ_BAD_REQUEST,
                    "请先生成学习路径，再使用练与测功能"
            );
        }
        return paths.get(0);
    }

    private PracticeSession loadSession(Long userId, Long sessionId) {
        PracticeSession session = practiceSessionMapper.selectById(sessionId);
        if (session == null || !userId.equals(session.getUserId())) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND,
                    ErrorCode.BIZ_NOT_FOUND,
                    "练测会话不存在"
            );
        }
        return session;
    }

    private PracticeQuestion loadQuestion(Long sessionId, Long questionId) {
        PracticeQuestion question = practiceQuestionMapper.selectByIdAndSessionId(questionId, sessionId);
        if (question == null) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND,
                    ErrorCode.BIZ_NOT_FOUND,
                    "题目不存在"
            );
        }
        return question;
    }

    private void ensureMode(PracticeSession session, String expected, String message) {
        if (!expected.equalsIgnoreCase(safe(session.getMode()))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, ErrorCode.BIZ_BAD_REQUEST, message);
        }
    }

    private void ensureOngoing(PracticeSession session, String message) {
        if (STATUS_COMPLETED.equalsIgnoreCase(safe(session.getStatus()))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, ErrorCode.BIZ_BAD_REQUEST, message);
        }
    }

    private boolean isExamTimeout(PracticeSession session) {
        if (!MODE_EXAM.equalsIgnoreCase(safe(session.getMode()))) return false;
        Integer duration = session.getDurationMinutes();
        if (duration == null || duration <= 0 || session.getCreatedAt() == null) return false;
        return LocalDateTime.now().isAfter(session.getCreatedAt().plusMinutes(duration));
    }

    private int sanitizeCount(Integer requested, int min, int max, int fallback) {
        int safe = requested == null ? fallback : requested;
        return Math.max(min, Math.min(max, safe));
    }

    private List<LearningNode> pickNodes(List<LearningNode> nodes, int questionCount) {
        Map<String, Integer> statusWeight = Map.of(
                "doing", 3,
                "todo", 2,
                "done", 1,
                "skipped", 0
        );

        List<LearningNode> sorted = nodes.stream()
                .sorted(Comparator
                        .comparingInt((LearningNode node) -> statusWeight.getOrDefault(safe(node.getStatus()).toLowerCase(Locale.ROOT), 1))
                        .reversed()
                        .thenComparing(node -> safeInt(node.getNodeOrder())))
                .collect(Collectors.toList());

        List<LearningNode> selected = new ArrayList<>();
        for (int i = 0; i < questionCount; i++) {
            selected.add(sorted.get(i % sorted.size()));
        }
        return selected;
    }

    private String nodeTopicName(LearningNode node) {
        String custom = safe(node.getCustomName()).trim();
        if (!custom.isBlank()) return custom;
        String knowledgeId = safe(node.getKnowledgeId()).trim();
        if (!knowledgeId.isBlank()) return knowledgeId;
        return "未命名知识点";
    }

    private List<String> buildOptions(String topic, List<String> allTopics, Random random) {
        Set<String> set = new LinkedHashSet<>();
        set.add(topic);

        List<String> candidates = new ArrayList<>();
        for (String item : allTopics) {
            if (!safe(item).equalsIgnoreCase(topic)) {
                candidates.add(item);
            }
        }
        for (String generic : GENERIC_DISTRACTORS) {
            if (!safe(generic).equalsIgnoreCase(topic)) {
                candidates.add(generic);
            }
        }

        Collections.shuffle(candidates, random);
        for (String candidate : candidates) {
            if (set.size() >= 4) break;
            if (candidate != null && !candidate.isBlank()) {
                set.add(candidate);
            }
        }

        List<String> options = new ArrayList<>(set);
        Collections.shuffle(options, random);
        return options;
    }

    private String findCorrectAnswer(List<String> options, String topic) {
        for (int i = 0; i < options.size() && i < OPTION_KEYS.size(); i++) {
            if (safe(options.get(i)).equalsIgnoreCase(topic)) {
                return OPTION_KEYS.get(i);
            }
        }
        return "A";
    }

    private String normalizeAnswer(String rawAnswer, List<String> options) {
        String normalized = safe(rawAnswer).trim();
        if (normalized.isBlank()) return "";

        String upper = normalized.toUpperCase(Locale.ROOT);
        if (OPTION_KEYS.contains(upper)) {
            return upper;
        }

        for (int i = 0; i < options.size() && i < OPTION_KEYS.size(); i++) {
            if (safe(options.get(i)).equalsIgnoreCase(normalized)) {
                return OPTION_KEYS.get(i);
            }
        }
        return upper;
    }

    private SessionStat recalculateSessionStat(Long sessionId) {
        List<PracticeQuestion> questions = practiceQuestionMapper.selectBySessionId(sessionId);
        int answered = 0;
        int correct = 0;
        for (PracticeQuestion question : questions) {
            if (safe(question.getUserAnswer()).isBlank()) {
                continue;
            }
            answered++;
            if (Integer.valueOf(1).equals(question.getIsCorrect())) {
                correct++;
            }
        }
        return new SessionStat(answered, correct);
    }

    private String gradeByScore(double score) {
        if (score >= 90) return "优秀";
        if (score >= 80) return "良好";
        if (score >= 60) return "及格";
        return "待提升";
    }

    private PracticeSessionVO toSessionVO(PracticeSession session, List<PracticeQuestion> questions) {
        boolean revealCorrect = MODE_PRACTICE.equalsIgnoreCase(safe(session.getMode()))
                || STATUS_COMPLETED.equalsIgnoreCase(safe(session.getStatus()));

        return PracticeSessionVO.builder()
                .id(session.getId())
                .pathId(session.getPathId())
                .mode(session.getMode())
                .status(session.getStatus())
                .totalQuestions(safeInt(session.getTotalQuestions()))
                .answeredCount(safeInt(session.getAnsweredCount()))
                .correctCount(safeInt(session.getCorrectCount()))
                .durationMinutes(session.getDurationMinutes())
                .score(session.getScore())
                .grade(session.getGrade())
                .createdAt(session.getCreatedAt())
                .submittedAt(session.getSubmittedAt())
                .questions(questions.stream().map(q -> toQuestionVO(q, revealCorrect)).collect(Collectors.toList()))
                .build();
    }

    private PracticeQuestionVO toQuestionVO(PracticeQuestion question, boolean revealCorrect) {
        return PracticeQuestionVO.builder()
                .id(question.getId())
                .knowledgeId(question.getKnowledgeId())
                .stem(question.getQuestionStem())
                .order(question.getQuestionOrder())
                .options(parseOptions(question.getOptionsJson()))
                .userAnswer(question.getUserAnswer())
                .correctAnswer(revealCorrect ? question.getCorrectAnswer() : null)
                .answered(!safe(question.getUserAnswer()).isBlank())
                .correct(revealCorrect ? Integer.valueOf(1).equals(question.getIsCorrect()) : null)
                .explanation(revealCorrect ? question.getExplanation() : null)
                .build();
    }

    private String toJson(List<String> options) {
        try {
            return objectMapper.writeValueAsString(options);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<String> parseOptions(String optionsJson) {
        if (optionsJson == null || optionsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(optionsJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private record SessionStat(int answeredCount, int correctCount) {}
}
