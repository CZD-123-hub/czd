package com.coding.assistant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.coding.assistant.dto.*;
import com.coding.assistant.entity.CodeSnippet;
import com.coding.assistant.entity.LearningNode;
import com.coding.assistant.entity.LearningPath;
import com.coding.assistant.entity.LearningRecord;
import com.coding.assistant.exception.BusinessException;
import com.coding.assistant.mapper.*;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProgressService {

    private final LearningRecordMapper learningRecordMapper;
    private final ConversationMapper conversationMapper;
    private final CodeSnippetMapper codeSnippetMapper;
    private final LearningPathMapper learningPathMapper;
    private final LearningNodeMapper learningNodeMapper;

    @Transactional
    public void recordAction(Long userId, String actionType, String targetId) {
        learningRecordMapper.insert(LearningRecord.builder().userId(userId).actionType(actionType).targetId(targetId).createdAt(LocalDateTime.now()).build());
    }

    public DashboardVO getDashboard(Long userId) {
        int totalDays = learningRecordMapper.countDistinctDaysByUserId(userId);
        int totalChats = conversationMapper.selectCount(new LambdaQueryWrapper<com.coding.assistant.entity.Conversation>().eq(com.coding.assistant.entity.Conversation::getUserId, userId)).intValue();
        int totalSnippets = codeSnippetMapper.selectCount(new LambdaQueryWrapper<CodeSnippet>().eq(CodeSnippet::getUserId, userId)).intValue();
        double knowledgeCoverage = Math.min(learningRecordMapper.countByUserIdGroupByActionType(userId).size() * 20.0, 100.0);

        LocalDateTime start = LocalDateTime.now().minusDays(30);
        List<ActivityVO> recent = learningRecordMapper.countByUserIdGroupByDate(userId, start, LocalDateTime.now()).stream()
                .map(m -> ActivityVO.builder().date(String.valueOf(m.get("date"))).count(((Number) m.get("count")).intValue()).build())
                .collect(Collectors.toList());

        return DashboardVO.builder().totalDays(totalDays).totalChats(totalChats).totalSnippets(totalSnippets).knowledgeCoverage(knowledgeCoverage).recentActivity(recent).build();
    }

    public SmartInsightsVO getSmartInsights(Long userId) {
        List<WeakAreaVO> weakAreas = buildWeakAreas(userId);
        return SmartInsightsVO.builder()
                .healthScore(calculateHealthScore(userId))
                .weakAreas(weakAreas)
                .weeklyPlan(buildWeeklyPlan(userId, weakAreas))
                .build();
    }

    @Transactional
    public void toggleWeeklyPlan(Long userId, String planId, boolean completed) {
        String action = "weekly_plan_done";
        int exists = learningRecordMapper.countByUserIdAndActionTypeAndTargetId(userId, action, planId);
        if (completed && exists == 0) recordAction(userId, action, planId);
        if (!completed && exists > 0) learningRecordMapper.deleteByUserIdAndActionTypeAndTargetId(userId, action, planId);
    }

    public HeatmapVO getHeatmap(Long userId, int year) {
        LocalDateTime start = LocalDateTime.of(year, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(year + 1, 1, 1, 0, 0);
        List<HeatmapDataVO> data = learningRecordMapper.countByUserIdGroupByDate(userId, start, end).stream()
                .map(m -> HeatmapDataVO.builder().date(String.valueOf(m.get("date"))).count(((Number) m.get("count")).intValue()).build())
                .collect(Collectors.toList());
        return HeatmapVO.builder().year(year).data(data).build();
    }

    public RadarVO getRadar(Long userId) {
        Map<String, Long> map = learningRecordMapper.countByUserIdGroupByActionType(userId).stream()
                .collect(Collectors.toMap(m -> String.valueOf(m.get("action_type")), m -> ((Number) m.get("count")).longValue(), (a, b) -> a));

        String[] keys = {"chat", "code_save", "path_learn", "graph_explore", "feedback"};
        String[] names = {"Chat", "Code Snippets", "Learning Paths", "Graph Exploration", "Feedback"};
        double max = map.values().stream().mapToLong(Long::longValue).max().orElse(1);

        List<String> categories = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        for (int i = 0; i < keys.length; i++) {
            categories.add(names[i]);
            values.add(max > 0 ? Math.round(map.getOrDefault(keys[i], 0L) * 100.0 / max) : 0.0);
        }
        return RadarVO.builder().categories(categories).values(values).build();
    }

    public byte[] generateReport(Long userId) {
        try {
            DashboardVO dashboard = getDashboard(userId);
            SmartInsightsVO insights = getSmartInsights(userId);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4);
            PdfWriter.getInstance(doc, baos);
            doc.open();

            Font titleFont = new Font(Font.FontFamily.HELVETICA, 22, Font.BOLD);
            Font sectionFont = new Font(Font.FontFamily.HELVETICA, 15, Font.BOLD);
            Font normal = new Font(Font.FontFamily.HELVETICA, 11, Font.NORMAL);

            Paragraph title = new Paragraph("Learning Progress Report", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            doc.add(title);
            Paragraph date = new Paragraph("Generated: " + LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE), normal);
            date.setAlignment(Element.ALIGN_CENTER);
            date.setSpacingAfter(16);
            doc.add(date);

            doc.add(new Paragraph("Overview", sectionFont));
            PdfPTable t1 = new PdfPTable(2); t1.setWidthPercentage(88);
            addRow(t1, "Learning Days", String.valueOf(dashboard.getTotalDays()), normal);
            addRow(t1, "Conversations", String.valueOf(dashboard.getTotalChats()), normal);
            addRow(t1, "Snippets", String.valueOf(dashboard.getTotalSnippets()), normal);
            addRow(t1, "Health Score", String.valueOf(insights.getHealthScore()), normal);
            t1.setSpacingAfter(12); doc.add(t1);

            doc.add(new Paragraph("Weak Areas Top 3", sectionFont));
            PdfPTable t2 = new PdfPTable(2); t2.setWidthPercentage(88);
            for (WeakAreaVO w : insights.getWeakAreas()) addRow(t2, w.getName(), w.getSuggestion(), normal);
            t2.setSpacingAfter(12); doc.add(t2);

            doc.add(new Paragraph("Next Week Plan", sectionFont));
            PdfPTable t3 = new PdfPTable(2); t3.setWidthPercentage(88);
            for (WeeklyPlanItemVO p : insights.getWeeklyPlan()) addRow(t3, p.getTitle(), p.getDescription(), normal);
            doc.add(t3);

            doc.close();
            return baos.toByteArray();
        } catch (DocumentException e) {
            throw new BusinessException(500, "Failed to generate report");
        }
    }

    private int calculateHealthScore(Long userId) {
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> daily = learningRecordMapper.countByUserIdGroupByDate(userId, today.minusDays(29).atStartOfDay(), today.plusDays(1).atStartOfDay());
        int activeDays = daily.size();
        int totalActions = daily.stream().mapToInt(m -> ((Number) m.get("count")).intValue()).sum();
        int avg = activeDays == 0 ? 0 : Math.round((float) totalActions / activeDays);
        int streak = currentStreak(daily, today);
        return Math.min(100, Math.min(40, activeDays * 2) + Math.min(30, streak * 5) + Math.min(30, avg * 5));
    }

    private int currentStreak(List<Map<String, Object>> daily, LocalDate today) {
        Set<String> days = daily.stream().map(m -> String.valueOf(m.get("date"))).collect(Collectors.toSet());
        int streak = 0;
        for (int i = 0; i < 30; i++) {
            if (days.contains(today.minusDays(i).toString())) streak++; else break;
        }
        return streak;
    }

    private List<WeakAreaVO> buildWeakAreas(Long userId) {
        List<LearningPath> paths = learningPathMapper.selectList(new LambdaQueryWrapper<LearningPath>().eq(LearningPath::getUserId, userId));
        if (paths.isEmpty()) {
            return List.of(
                    new WeakAreaVO("db", "数据库与缓存", 40, "完成 2 次 SQL 优化练习"),
                    new WeakAreaVO("algorithm", "算法与数据结构", 45, "每周完成 3 道中等题"),
                    new WeakAreaVO("backend", "后端开发", 50, "补齐鉴权与异常处理实践")
            );
        }

        Map<String, int[]> map = new HashMap<>();
        for (LearningPath path : paths) {
            for (LearningNode n : learningNodeMapper.selectByPathId(path.getId())) {
                String key = areaOf(n.getKnowledgeId());
                int[] arr = map.computeIfAbsent(key, k -> new int[]{0, 0});
                arr[0]++; if ("done".equalsIgnoreCase(n.getStatus())) arr[1]++;
            }
        }

        return map.entrySet().stream()
                .map(e -> {
                    int total = e.getValue()[0], done = e.getValue()[1];
                    int score = total == 0 ? 0 : (int) Math.round(done * 100.0 / total);
                    return new WeakAreaVO(e.getKey(), areaName(e.getKey()), score, areaSuggestion(e.getKey(), score));
                })
                .sorted(Comparator.comparingInt(WeakAreaVO::getScore))
                .limit(3)
                .collect(Collectors.toList());
    }

    private List<WeeklyPlanItemVO> buildWeeklyPlan(Long userId, List<WeakAreaVO> weakAreas) {
        List<String> doneIds = learningRecordMapper.selectDistinctTargetIdsByUserIdAndActionType(userId, "weekly_plan_done");
        List<WeeklyPlanItemVO> plans = new ArrayList<>();
        for (WeakAreaVO w : weakAreas) {
            String id = "plan:" + w.getKey();
            plans.add(new WeeklyPlanItemVO(id, "补强「" + w.getName() + "」", planOf(w.getKey()), doneIds.contains(id)));
        }
        while (plans.size() < 3) {
            String id = "plan:review:" + plans.size();
            plans.add(new WeeklyPlanItemVO(id, "复盘本周学习", "整理 5 条高质量问答并沉淀为代码片段", doneIds.contains(id)));
        }
        return plans.subList(0, 3);
    }

    private String areaOf(String k) {
        k = k == null ? "" : k.toLowerCase();
        if (k.contains("mysql") || k.contains("sql") || k.contains("redis") || k.contains("database")) return "db";
        if (k.contains("vue") || k.contains("react") || k.contains("front") || k.contains("js")) return "frontend";
        if (k.contains("docker") || k.contains("k8") || k.contains("devops") || k.contains("deploy")) return "devops";
        if (k.contains("spring") || k.contains("java") || k.contains("mybatis") || k.contains("api")) return "backend";
        return "algorithm";
    }

    private String areaName(String key) {
        return switch (key) {
            case "db" -> "数据库与缓存";
            case "frontend" -> "前端工程";
            case "backend" -> "后端开发";
            case "devops" -> "DevOps 与部署";
            default -> "算法与数据结构";
        };
    }

    private String areaSuggestion(String key, int score) {
        if (score >= 80) return "掌握较好，建议通过项目实战巩固";
        return switch (key) {
            case "db" -> "完成数据库优化实操并复习缓存策略";
            case "frontend" -> "完成 1 次组件重构并补齐性能优化清单";
            case "backend" -> "完成 1 个鉴权接口与异常处理优化";
            case "devops" -> "完成一次 Docker Compose 全链路部署";
            default -> "完成 3 道算法题并复盘";
        };
    }

    private String planOf(String key) {
        return switch (key) {
            case "db" -> "完成 2 次 SQL 优化练习并总结索引命中规则";
            case "frontend" -> "完成 1 次组件重构并补齐性能优化清单";
            case "backend" -> "完成 1 个鉴权接口和 1 个异常处理优化";
            case "devops" -> "完成本地一键部署并验证回滚流程";
            default -> "完成 3 道算法题并沉淀解题模板";
        };
    }

    private void addRow(PdfPTable table, String leftText, String rightText, Font font) {
        PdfPCell left = new PdfPCell(new Phrase(leftText, font));
        left.setPadding(8); table.addCell(left);
        PdfPCell right = new PdfPCell(new Phrase(rightText, font));
        right.setPadding(8); right.setHorizontalAlignment(Element.ALIGN_RIGHT); table.addCell(right);
    }
}
