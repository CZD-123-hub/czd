package com.coding.assistant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.coding.assistant.dto.UserProfileVO;
import com.coding.assistant.entity.CodeSnippet;
import com.coding.assistant.entity.LearningNode;
import com.coding.assistant.entity.LearningPath;
import com.coding.assistant.entity.User;
import com.coding.assistant.mapper.CodeSnippetMapper;
import com.coding.assistant.mapper.LearningNodeMapper;
import com.coding.assistant.mapper.LearningPathMapper;
import com.coding.assistant.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserMapper userMapper;
    private final LearningPathMapper learningPathMapper;
    private final LearningNodeMapper learningNodeMapper;
    private final CodeSnippetMapper codeSnippetMapper;

    public UserProfileVO buildProfile(Long userId) {
        User user = userMapper.selectById(userId);
        String level = user != null ? user.getLevel() : "beginner";

        LearningPath latestPath = findLatestPath(userId);
        String primaryGoal = latestPath != null ? latestPath.getTarget() : "未设置学习目标";
        List<String> weakPoints = latestPath != null ? extractWeakPoints(latestPath.getId()) : List.of();
        List<String> preferredLanguages = extractPreferredLanguages(userId);
        String learningStyle = inferLearningStyle(preferredLanguages, weakPoints);
        String nextTaskHint = buildNextTaskHint(weakPoints, primaryGoal);

        return UserProfileVO.builder()
                .level(level)
                .primaryGoal(primaryGoal)
                .weakPoints(weakPoints)
                .preferredLanguages(preferredLanguages)
                .learningStyle(learningStyle)
                .nextTaskHint(nextTaskHint)
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private LearningPath findLatestPath(Long userId) {
        return learningPathMapper.selectOne(
                new LambdaQueryWrapper<LearningPath>()
                        .eq(LearningPath::getUserId, userId)
                        .orderByDesc(LearningPath::getUpdatedAt)
                        .last("LIMIT 1")
        );
    }

    private List<String> extractWeakPoints(Long pathId) {
        List<LearningNode> nodes = learningNodeMapper.selectByPathId(pathId);
        if (nodes == null || nodes.isEmpty()) {
            return List.of();
        }

        return nodes.stream()
                .sorted(Comparator.comparing(LearningNode::getNodeOrder))
                .filter(node -> "todo".equals(node.getStatus()) || "doing".equals(node.getStatus()))
                .map(this::nodeDisplayName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .limit(3)
                .collect(Collectors.toList());
    }

    private List<String> extractPreferredLanguages(Long userId) {
        List<CodeSnippet> snippets = codeSnippetMapper.selectList(
                new LambdaQueryWrapper<CodeSnippet>()
                        .eq(CodeSnippet::getUserId, userId)
                        .orderByDesc(CodeSnippet::getUseCount)
                        .orderByDesc(CodeSnippet::getUpdatedAt)
                        .last("LIMIT 100")
        );

        if (snippets == null || snippets.isEmpty()) {
            return List.of();
        }

        Map<String, Integer> languageScore = new LinkedHashMap<>();
        for (CodeSnippet snippet : snippets) {
            String language = snippet.getLanguage();
            if (language == null || language.isBlank()) {
                continue;
            }
            int weight = (snippet.getUseCount() == null ? 0 : snippet.getUseCount()) + 1;
            languageScore.merge(language.trim().toLowerCase(Locale.ROOT), weight, Integer::sum);
        }

        return languageScore.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .map(Map.Entry::getKey)
                .limit(3)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private String inferLearningStyle(List<String> preferredLanguages, List<String> weakPoints) {
        if (preferredLanguages.size() >= 2 && weakPoints.size() <= 1) {
            return "实践驱动";
        }
        if (weakPoints.size() >= 2) {
            return "引导学习";
        }
        return "混合学习";
    }

    private String buildNextTaskHint(List<String> weakPoints, String primaryGoal) {
        if (!weakPoints.isEmpty()) {
            return "优先补齐薄弱点：" + weakPoints.get(0);
        }
        if (primaryGoal != null && !primaryGoal.isBlank()) {
            return "围绕目标继续推进：" + primaryGoal;
        }
        return "先设置一个明确学习目标，再开始路径推进。";
    }

    private String nodeDisplayName(LearningNode node) {
        if (node.getCustomName() != null && !node.getCustomName().isBlank()) {
            return node.getCustomName();
        }
        return node.getKnowledgeId();
    }
}
