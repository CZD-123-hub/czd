package com.coding.assistant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.coding.assistant.dto.*;
import com.coding.assistant.entity.CodeSnippet;
import com.coding.assistant.entity.LearningNode;
import com.coding.assistant.entity.LearningPath;
import com.coding.assistant.entity.LearningRecord;
import com.coding.assistant.entity.PracticeSession;
import com.coding.assistant.exception.BusinessException;
import com.coding.assistant.exception.ErrorCode;
import com.coding.assistant.mapper.*;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
    private final PracticeSessionMapper practiceSessionMapper;
    private static final int DASHBOARD_PERIOD_DAYS = 30;

    @Transactional
    public void recordAction(Long userId, String actionType, String targetId) {
        String normalizedAction = LearningActionType.normalize(actionType);
        if (normalizedAction.isBlank()) {
            return;
        }
        learningRecordMapper.insert(
                LearningRecord.builder()
                        .userId(userId)
                        .actionType(normalizedAction)
                        .targetId(targetId)
                        .createdAt(LocalDateTime.now())
                        .build()
        );
    }

    public DashboardVO getDashboard(Long userId) {
        int totalDays = learningRecordMapper.countDistinctDaysByUserId(userId);
        int totalChats = conversationMapper.selectCount(new LambdaQueryWrapper<com.coding.assistant.entity.Conversation>().eq(com.coding.assistant.entity.Conversation::getUserId, userId)).intValue();
        int totalSnippets = codeSnippetMapper.selectCount(new LambdaQueryWrapper<CodeSnippet>().eq(CodeSnippet::getUserId, userId)).intValue();
        Map<String, Long> overallActionCounts = aggregateNormalizedActionCounts(userId);
        long coveredActions = overallActionCounts.entrySet().stream()
                .filter(entry -> LearningActionType.isCoreAction(entry.getKey()) && entry.getValue() > 0)
                .count();
        double knowledgeCoverage = Math.min(coveredActions * 20.0, 100.0);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime currentStart = now.minusDays(DASHBOARD_PERIOD_DAYS);
        LocalDateTime previousStart = currentStart.minusDays(DASHBOARD_PERIOD_DAYS);

        List<Map<String, Object>> currentDaily = learningRecordMapper.countByUserIdGroupByDate(userId, currentStart, now);
        List<Map<String, Object>> previousDaily = learningRecordMapper.countByUserIdGroupByDate(userId, previousStart, currentStart);
        int currentActiveDays = currentDaily.size();
        int previousActiveDays = previousDaily.size();

        int currentChats = conversationMapper.selectCount(
                new LambdaQueryWrapper<com.coding.assistant.entity.Conversation>()
                        .eq(com.coding.assistant.entity.Conversation::getUserId, userId)
                        .ge(com.coding.assistant.entity.Conversation::getCreatedAt, currentStart)
                        .lt(com.coding.assistant.entity.Conversation::getCreatedAt, now)
        ).intValue();
        int previousChats = conversationMapper.selectCount(
                new LambdaQueryWrapper<com.coding.assistant.entity.Conversation>()
                        .eq(com.coding.assistant.entity.Conversation::getUserId, userId)
                        .ge(com.coding.assistant.entity.Conversation::getCreatedAt, previousStart)
                        .lt(com.coding.assistant.entity.Conversation::getCreatedAt, currentStart)
        ).intValue();

        int currentSnippets = codeSnippetMapper.selectCount(
                new LambdaQueryWrapper<CodeSnippet>()
                        .eq(CodeSnippet::getUserId, userId)
                        .ge(CodeSnippet::getCreatedAt, currentStart)
                        .lt(CodeSnippet::getCreatedAt, now)
        ).intValue();
        int previousSnippets = codeSnippetMapper.selectCount(
                new LambdaQueryWrapper<CodeSnippet>()
                        .eq(CodeSnippet::getUserId, userId)
                        .ge(CodeSnippet::getCreatedAt, previousStart)
                        .lt(CodeSnippet::getCreatedAt, currentStart)
        ).intValue();

        double currentCoverage = computeKnowledgeCoverage(aggregateNormalizedActionCounts(userId, currentStart, now));
        double previousCoverage = computeKnowledgeCoverage(aggregateNormalizedActionCounts(userId, previousStart, currentStart));

        List<ActivityVO> recent = currentDaily.stream()
                .map(m -> ActivityVO.builder().date(String.valueOf(m.get("date"))).count(((Number) m.get("count")).intValue()).build())
                .collect(Collectors.toList());

        DashboardComparisonsVO comparisons = DashboardComparisonsVO.builder()
                .totalDays(buildComparison(currentActiveDays, previousActiveDays))
                .totalChats(buildComparison(currentChats, previousChats))
                .totalSnippets(buildComparison(currentSnippets, previousSnippets))
                .knowledgeCoverage(buildComparison(currentCoverage, previousCoverage))
                .build();

        List<String> coveredActionKeys = LearningActionType.radarKeys().stream()
                .filter(key -> overallActionCounts.getOrDefault(key, 0L) > 0)
                .collect(Collectors.toList());
        CoverageDetailVO coverageDetail = CoverageDetailVO.builder()
                .coveredCoreActions((int) coveredActions)
                .totalCoreActions(LearningActionType.radarKeys().size())
                .coveredActionKeys(coveredActionKeys)
                .build();
        ExamSummaryVO examSummary = buildExamSummary(userId);

        return DashboardVO.builder()
                .totalDays(totalDays)
                .totalChats(totalChats)
                .totalSnippets(totalSnippets)
                .knowledgeCoverage(knowledgeCoverage)
                .periodDays(DASHBOARD_PERIOD_DAYS)
                .comparisons(comparisons)
                .coverageDetail(coverageDetail)
                .examSummary(examSummary)
                .recentActivity(recent)
                .build();
    }

    private ExamSummaryVO buildExamSummary(Long userId) {
        List<PracticeSession> sessions = practiceSessionMapper.selectList(
                new LambdaQueryWrapper<PracticeSession>()
                        .eq(PracticeSession::getUserId, userId)
                        .eq(PracticeSession::getMode, "exam")
                        .eq(PracticeSession::getStatus, "completed")
                        .isNotNull(PracticeSession::getScore)
                        .isNotNull(PracticeSession::getSubmittedAt)
                        .orderByDesc(PracticeSession::getSubmittedAt)
                        .last("LIMIT 12")
        );
        if (sessions == null || sessions.isEmpty()) {
            return ExamSummaryVO.builder()
                    .completedCount(0)
                    .avgScore(0.0)
                    .passRate(0.0)
                    .latestScore(null)
                    .latestGrade("")
                    .latestSubmittedAt("")
                    .recentTrend(List.of())
                    .build();
        }

        int completedCount = sessions.size();
        double avgScore = roundTo2(
                sessions.stream()
                        .map(PracticeSession::getScore)
                        .filter(Objects::nonNull)
                        .mapToDouble(Double::doubleValue)
                        .average()
                        .orElse(0.0)
        );
        long passCount = sessions.stream()
                .map(PracticeSession::getScore)
                .filter(Objects::nonNull)
                .filter(score -> score >= 60.0)
                .count();
        double passRate = roundTo2(passCount * 100.0 / completedCount);

        PracticeSession latest = sessions.get(0);
        List<ExamTrendPointVO> trend = sessions.stream()
                .sorted(Comparator.comparing(PracticeSession::getSubmittedAt))
                .limit(8)
                .map(session -> ExamTrendPointVO.builder()
                        .date(session.getSubmittedAt().toLocalDate().toString())
                        .score(roundTo2(session.getScore() == null ? 0.0 : session.getScore()))
                        .grade(session.getGrade() == null ? "" : session.getGrade())
                        .build())
                .toList();

        return ExamSummaryVO.builder()
                .completedCount(completedCount)
                .avgScore(avgScore)
                .passRate(passRate)
                .latestScore(latest.getScore() == null ? 0.0 : roundTo2(latest.getScore()))
                .latestGrade(latest.getGrade() == null ? "" : latest.getGrade())
                .latestSubmittedAt(latest.getSubmittedAt().toString())
                .recentTrend(trend)
                .build();
    }

    public SmartInsightsVO getSmartInsights(Long userId) {
        List<WeakAreaVO> weakAreas = buildWeakAreas(userId);
        HealthBreakdownVO healthBreakdown = calculateHealthBreakdown(userId);
        return SmartInsightsVO.builder()
                .healthScore(calculateHealthScore(healthBreakdown))
                .healthBreakdown(healthBreakdown)
                .weakAreas(weakAreas)
                .weeklyPlan(buildWeeklyPlan(userId, weakAreas))
                .build();
    }

    @Transactional
    public void toggleWeeklyPlan(Long userId, String planId, boolean completed) {
        String action = LearningActionType.WEEKLY_PLAN_DONE;
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
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.minusDays(DASHBOARD_PERIOD_DAYS);
        Map<String, Long> map = aggregateNormalizedActionCounts(userId, start, now);
        double max = map.values().stream().mapToLong(Long::longValue).max().orElse(1);

        List<String> categories = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        List<Long> rawCounts = new ArrayList<>();
        for (String key : LearningActionType.radarKeys()) {
            categories.add(LearningActionType.radarLabel(key));
            long count = map.getOrDefault(key, 0L);
            rawCounts.add(count);
            values.add(max > 0 ? Math.round(count * 100.0 / max) : 0.0);
        }
        return RadarVO.builder()
                .categories(categories)
                .values(values)
                .rawCounts(rawCounts)
                .maxCount((long) max)
                .periodDays(DASHBOARD_PERIOD_DAYS)
                .build();
    }

    public byte[] generateReport(Long userId) {
        try {
            DashboardVO dashboard = getDashboard(userId);
            SmartInsightsVO insights = getSmartInsights(userId);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4, 40, 40, 46, 40);
            PdfWriter.getInstance(doc, baos);
            doc.open();

            BaseFont zhBase = BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED);
            Font titleFont = new Font(zhBase, 20, Font.BOLD, new BaseColor(30, 64, 122));
            Font subtitleFont = new Font(zhBase, 10.5f, Font.NORMAL, new BaseColor(111, 123, 147));
            Font sectionFont = new Font(zhBase, 13, Font.BOLD, new BaseColor(38, 67, 128));
            Font tableHeaderFont = new Font(zhBase, 10.5f, Font.BOLD, BaseColor.WHITE);
            Font normal = new Font(zhBase, 10.5f, Font.NORMAL, new BaseColor(46, 56, 78));
            Font emphasize = new Font(zhBase, 10.5f, Font.BOLD, new BaseColor(46, 56, 78));

            Paragraph title = new Paragraph("学习进度分析报告", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(6);
            doc.add(title);

            String generatedDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            Paragraph subtitle = new Paragraph("生成时间：" + generatedDate + "    统计窗口：最近 " + DASHBOARD_PERIOD_DAYS + " 天", subtitleFont);
            subtitle.setAlignment(Element.ALIGN_CENTER);
            subtitle.setSpacingAfter(14);
            doc.add(subtitle);

            addSectionTitle(doc, "一、学习总览", sectionFont);
            PdfPTable overview = new PdfPTable(new float[]{1.15f, 1.45f});
            overview.setWidthPercentage(100);
            addHeaderCell(overview, "指标", tableHeaderFont);
            addHeaderCell(overview, "当前值", tableHeaderFont);
            addBodyCell(overview, "学习天数", normal, Element.ALIGN_LEFT);
            addBodyCell(overview, dashboard.getTotalDays() + " 天", emphasize, Element.ALIGN_RIGHT);
            addBodyCell(overview, "对话次数", normal, Element.ALIGN_LEFT);
            addBodyCell(overview, dashboard.getTotalChats() + " 次", emphasize, Element.ALIGN_RIGHT);
            addBodyCell(overview, "代码片段数", normal, Element.ALIGN_LEFT);
            addBodyCell(overview, dashboard.getTotalSnippets() + " 个", emphasize, Element.ALIGN_RIGHT);
            addBodyCell(overview, "知识覆盖率", normal, Element.ALIGN_LEFT);
            addBodyCell(overview, roundTo2(dashboard.getKnowledgeCoverage()) + "%", emphasize, Element.ALIGN_RIGHT);
            addBodyCell(overview, "学习健康度得分", normal, Element.ALIGN_LEFT);
            addBodyCell(overview, insights.getHealthScore() + " / 100", emphasize, Element.ALIGN_RIGHT);
            if (dashboard.getCoverageDetail() != null) {
                addBodyCell(overview, "核心行为覆盖", normal, Element.ALIGN_LEFT);
                addBodyCell(
                        overview,
                        dashboard.getCoverageDetail().getCoveredCoreActions() + " / " + dashboard.getCoverageDetail().getTotalCoreActions(),
                        emphasize,
                        Element.ALIGN_RIGHT
                );
            }
            overview.setSpacingAfter(12);
            doc.add(overview);

            addSectionTitle(doc, "二、趋势对比（本期 vs 上期）", sectionFont);
            PdfPTable trend = new PdfPTable(new float[]{1.2f, 1f, 1.2f});
            trend.setWidthPercentage(100);
            addHeaderCell(trend, "指标", tableHeaderFont);
            addHeaderCell(trend, "当前值", tableHeaderFont);
            addHeaderCell(trend, "变化", tableHeaderFont);

            DashboardComparisonsVO comparisons = dashboard.getComparisons();
            addTrendRow(trend, "活跃天数", comparisons == null ? null : comparisons.getTotalDays(), "天", false, normal);
            addTrendRow(trend, "对话次数", comparisons == null ? null : comparisons.getTotalChats(), "次", false, normal);
            addTrendRow(trend, "代码片段", comparisons == null ? null : comparisons.getTotalSnippets(), "个", false, normal);
            addTrendRow(trend, "知识覆盖率", comparisons == null ? null : comparisons.getKnowledgeCoverage(), "%", true, normal);
            trend.setSpacingAfter(12);
            doc.add(trend);

            if (insights.getHealthBreakdown() != null) {
                addSectionTitle(doc, "三、学习健康度拆解", sectionFont);
                HealthBreakdownVO breakdown = insights.getHealthBreakdown();
                PdfPTable health = new PdfPTable(new float[]{1.2f, 1f, 1f});
                health.setWidthPercentage(100);
                addHeaderCell(health, "维度", tableHeaderFont);
                addHeaderCell(health, "当前值", tableHeaderFont);
                addHeaderCell(health, "得分", tableHeaderFont);
                addBodyCell(health, "活跃天数", normal, Element.ALIGN_LEFT);
                addBodyCell(health, breakdown.getActiveDays() + " 天", normal, Element.ALIGN_RIGHT);
                addBodyCell(health, breakdown.getActiveDaysScore() + " / 40", normal, Element.ALIGN_RIGHT);
                addBodyCell(health, "连续学习", normal, Element.ALIGN_LEFT);
                addBodyCell(health, breakdown.getStreak() + " 天", normal, Element.ALIGN_RIGHT);
                addBodyCell(health, breakdown.getStreakScore() + " / 30", normal, Element.ALIGN_RIGHT);
                addBodyCell(health, "日均动作", normal, Element.ALIGN_LEFT);
                addBodyCell(health, roundTo2(breakdown.getAvgDailyActions()) + " 次", normal, Element.ALIGN_RIGHT);
                addBodyCell(health, breakdown.getAvgActionsScore() + " / 30", normal, Element.ALIGN_RIGHT);
                health.setSpacingAfter(12);
                doc.add(health);
            }

            addSectionTitle(doc, "四、待提升领域前三", sectionFont);
            PdfPTable weakTable = new PdfPTable(new float[]{1f, 1f, 2.1f});
            weakTable.setWidthPercentage(100);
            addHeaderCell(weakTable, "领域", tableHeaderFont);
            addHeaderCell(weakTable, "进度", tableHeaderFont);
            addHeaderCell(weakTable, "建议", tableHeaderFont);
            for (WeakAreaVO weakArea : insights.getWeakAreas()) {
                addBodyCell(weakTable, safeText(weakArea.getName(), "未命名领域"), normal, Element.ALIGN_LEFT);
                String progressText = weakArea.getTotalNodes() != null && weakArea.getTotalNodes() > 0
                        ? weakArea.getDoneNodes() + "/" + weakArea.getTotalNodes() + "（" + weakArea.getScore() + " 分）"
                        : weakArea.getScore() + " 分";
                addBodyCell(weakTable, progressText, normal, Element.ALIGN_RIGHT);
                addBodyCell(weakTable, safeText(weakArea.getSuggestion(), "建议结合项目实践持续强化。"), normal, Element.ALIGN_LEFT);
            }
            weakTable.setSpacingAfter(12);
            doc.add(weakTable);

            addSectionTitle(doc, "五、本周行动计划", sectionFont);
            PdfPTable planTable = new PdfPTable(new float[]{0.8f, 1.2f, 2.1f});
            planTable.setWidthPercentage(100);
            addHeaderCell(planTable, "状态", tableHeaderFont);
            addHeaderCell(planTable, "行动", tableHeaderFont);
            addHeaderCell(planTable, "描述", tableHeaderFont);
            for (WeeklyPlanItemVO planItem : insights.getWeeklyPlan()) {
                addBodyCell(planTable, planItem.isCompleted() ? "已完成" : "待完成", normal, Element.ALIGN_CENTER);
                addBodyCell(planTable, safeText(planItem.getTitle(), "未命名计划"), normal, Element.ALIGN_LEFT);
                String detail = safeText(planItem.getDescription(), "暂无描述");
                if (planItem.getExpectedImpact() != null && !planItem.getExpectedImpact().isBlank()) {
                    detail = detail + "；预期收益：" + planItem.getExpectedImpact();
                }
                addBodyCell(planTable, detail, normal, Element.ALIGN_LEFT);
            }
            doc.add(planTable);

            Paragraph footer = new Paragraph("建议每周导出一次报告，用于复盘进展并形成学习闭环。", subtitleFont);
            footer.setSpacingBefore(10);
            footer.setAlignment(Element.ALIGN_RIGHT);
            doc.add(footer);

            doc.close();
            return baos.toByteArray();
        } catch (DocumentException | IOException e) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_SERVER_ERROR,
                    ErrorCode.BIZ_EXPORT_FAILED,
                    "学习报告导出失败，请稍后重试。"
            );
        }
    }

    private HealthBreakdownVO calculateHealthBreakdown(Long userId) {
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> daily = learningRecordMapper.countByUserIdGroupByDate(userId, today.minusDays(29).atStartOfDay(), today.plusDays(1).atStartOfDay());
        int activeDays = daily.size();
        int totalActions = daily.stream().mapToInt(m -> ((Number) m.get("count")).intValue()).sum();
        int avg = activeDays == 0 ? 0 : Math.round((float) totalActions / activeDays);
        int streak = currentStreak(daily, today);
        int activeDaysScore = Math.min(40, activeDays * 2);
        int streakScore = Math.min(30, streak * 5);
        int avgActionsScore = Math.min(30, avg * 5);
        double avgDailyActions = activeDays == 0 ? 0 : Math.round((totalActions * 100.0 / activeDays)) / 100.0;
        return HealthBreakdownVO.builder()
                .activeDaysScore(activeDaysScore)
                .streakScore(streakScore)
                .avgActionsScore(avgActionsScore)
                .activeDays(activeDays)
                .streak(streak)
                .avgDailyActions(avgDailyActions)
                .build();
    }

    private int calculateHealthScore(HealthBreakdownVO breakdown) {
        if (breakdown == null) {
            return 0;
        }
        return Math.min(100, breakdown.getActiveDaysScore() + breakdown.getStreakScore() + breakdown.getAvgActionsScore());
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
                    new WeakAreaVO("db", "数据库与缓存", 40, 0, 0, "完成 2 题 SQL 优化练习并总结索引命中规则"),
                    new WeakAreaVO("algorithm", "算法与数据结构", 45, 0, 0, "每周完成 3 道中等难度算法题并沉淀模板"),
                    new WeakAreaVO("backend", "后端工程能力", 50, 0, 0, "强化鉴权与异常处理实践")
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
                    return new WeakAreaVO(e.getKey(), areaName(e.getKey()), score, done, total, areaSuggestion(e.getKey(), score));
                })
                .sorted(Comparator.comparingInt(WeakAreaVO::getScore))
                .limit(3)
                .collect(Collectors.toList());
    }

    private List<WeeklyPlanItemVO> buildWeeklyPlan(Long userId, List<WeakAreaVO> weakAreas) {
        List<String> doneIds = learningRecordMapper.selectDistinctTargetIdsByUserIdAndActionType(userId, LearningActionType.WEEKLY_PLAN_DONE);
        List<WeeklyPlanItemVO> plans = new ArrayList<>();
        for (WeakAreaVO w : weakAreas) {
            String id = "plan:" + w.getKey();
            plans.add(new WeeklyPlanItemVO(id, "强化：" + w.getName(), planOf(w.getKey()), expectedImpactOf(w), doneIds.contains(id)));
        }
        while (plans.size() < 3) {
            String id = "plan:review:" + plans.size();
            plans.add(new WeeklyPlanItemVO(id, "复盘本周学习", "整理 5 条高质量问答结果并沉淀为可复用代码片段", "提升复盘质量与知识迁移能力", doneIds.contains(id)));
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
            case "frontend" -> "前端工程能力";
            case "backend" -> "后端工程能力";
            case "devops" -> "部署与运维能力";
            default -> "算法与数据结构";
        };
    }

    private String areaSuggestion(String key, int score) {
        if (score >= 80) return "掌握较好，建议结合项目实践持续强化。";
        return switch (key) {
            case "db" -> "练习数据库调优与缓存策略，并维护故障排查清单。";
            case "frontend" -> "完成 1 次组件重构，并补充前端性能检查清单。";
            case "backend" -> "优化 1 个鉴权接口和 1 条异常处理链路。";
            case "devops" -> "完成 1 次 Docker Compose 全链路部署演练。";
            default -> "完成 3 道中等难度算法题并沉淀解题模板。";
        };
    }

    private String planOf(String key) {
        return switch (key) {
            case "db" -> "完成 2 题 SQL 优化练习并总结索引命中规则。";
            case "frontend" -> "完成 1 次组件重构并补充性能优化检查清单。";
            case "backend" -> "完成 1 个鉴权接口和 1 条异常处理优化。";
            case "devops" -> "完成本地一键部署与回滚验证流程。";
            default -> "完成 3 道算法题并整理解题模板。";
        };
    }

    private String expectedImpactOf(WeakAreaVO weakArea) {
        if (weakArea == null) {
            return "提升学习稳定性与知识闭环能力";
        }
        Integer totalNodes = weakArea.getTotalNodes();
        Integer doneNodes = weakArea.getDoneNodes();
        if (totalNodes != null && totalNodes > 0 && doneNodes != null) {
            int nextDone = Math.min(totalNodes, doneNodes + 1);
            int nextScore = (int) Math.round(nextDone * 100.0 / totalNodes);
            return "预计将「" + weakArea.getName() + "」完成率提升至约 " + nextScore + " 分";
        }
        return "预计提升「" + weakArea.getName() + "」学习质量";
    }

    private MetricComparisonVO buildComparison(double current, double previous) {
        double delta = current - previous;
        String trend = Math.abs(delta) < 0.0001 ? "flat" : (delta > 0 ? "up" : "down");
        return MetricComparisonVO.builder()
                .current(roundTo2(current))
                .previous(roundTo2(previous))
                .delta(roundTo2(delta))
                .trend(trend)
                .build();
    }

    private double computeKnowledgeCoverage(Map<String, Long> actionCounts) {
        long coveredActions = LearningActionType.radarKeys().stream()
                .filter(key -> actionCounts.getOrDefault(key, 0L) > 0)
                .count();
        return Math.min(coveredActions * 20.0, 100.0);
    }

    private double roundTo2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private Map<String, Long> aggregateNormalizedActionCounts(Long userId) {
        Map<String, Long> normalized = new HashMap<>();
        for (Map<String, Object> row : learningRecordMapper.countByUserIdGroupByActionType(userId)) {
            String rawType = String.valueOf(row.get("action_type"));
            String normalizedType = LearningActionType.normalize(rawType);
            if (normalizedType.isBlank()) {
                continue;
            }
            long count = ((Number) row.get("count")).longValue();
            normalized.merge(normalizedType, count, Long::sum);
        }
        return normalized;
    }

    private Map<String, Long> aggregateNormalizedActionCounts(Long userId, LocalDateTime start, LocalDateTime end) {
        Map<String, Long> normalized = new HashMap<>();
        for (LearningRecord record : learningRecordMapper.selectByUserIdAndDateRange(userId, start, end)) {
            String normalizedType = LearningActionType.normalize(record.getActionType());
            if (normalizedType.isBlank()) {
                continue;
            }
            normalized.merge(normalizedType, 1L, Long::sum);
        }
        return normalized;
    }

    private void addSectionTitle(Document document, String title, Font font) throws DocumentException {
        Paragraph sectionTitle = new Paragraph(title, font);
        sectionTitle.setSpacingBefore(4);
        sectionTitle.setSpacingAfter(8);
        document.add(sectionTitle);
    }

    private void addHeaderCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(7);
        cell.setBackgroundColor(new BaseColor(82, 118, 196));
        cell.setBorderColor(new BaseColor(210, 220, 240));
        table.addCell(cell);
    }

    private void addBodyCell(PdfPTable table, String text, Font font, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(safeText(text, "-"), font));
        cell.setHorizontalAlignment(align);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(7);
        cell.setBackgroundColor(new BaseColor(250, 252, 255));
        cell.setBorderColor(new BaseColor(222, 230, 246));
        table.addCell(cell);
    }

    private void addTrendRow(PdfPTable table, String metricName, MetricComparisonVO comparison, String unit, boolean percentMetric, Font font) {
        addBodyCell(table, metricName, font, Element.ALIGN_LEFT);
        addBodyCell(table, formatCurrentValue(comparison, unit, percentMetric), font, Element.ALIGN_RIGHT);
        addBodyCell(table, formatDeltaValue(comparison, unit, percentMetric), font, Element.ALIGN_RIGHT);
    }

    private String formatCurrentValue(MetricComparisonVO comparison, String unit, boolean percentMetric) {
        if (comparison == null) {
            return "-";
        }
        double current = comparison.getCurrent();
        if (percentMetric) {
            return roundTo2(current) + "%";
        }
        return Math.round(current) + unit;
    }

    private String formatDeltaValue(MetricComparisonVO comparison, String unit, boolean percentMetric) {
        if (comparison == null) {
            return "无数据";
        }
        double delta = comparison.getDelta();
        if (Math.abs(delta) < 0.0001) {
            return "持平";
        }
        String sign = delta > 0 ? "+" : "";
        if (percentMetric) {
            return sign + roundTo2(delta) + "%";
        }
        return sign + Math.round(delta) + unit;
    }

    private String safeText(String text, String fallback) {
        if (text == null || text.isBlank()) {
            return fallback;
        }
        return text;
    }

    private void addRow(PdfPTable table, String leftText, String rightText, Font font) {
        PdfPCell left = new PdfPCell(new Phrase(leftText, font));
        left.setPadding(8); table.addCell(left);
        PdfPCell right = new PdfPCell(new Phrase(rightText, font));
        right.setPadding(8); right.setHorizontalAlignment(Element.ALIGN_RIGHT); table.addCell(right);
    }
}
