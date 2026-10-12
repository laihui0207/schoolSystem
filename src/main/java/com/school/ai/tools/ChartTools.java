package com.school.ai.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.module.exam.entity.ExamSubject;
import com.school.module.exam.mapper.ExamSubjectMapper;
import com.school.module.score.dto.ClassScoreItemDto;
import com.school.module.score.service.ScoreService;
import com.school.module.stat.dto.ClassStatResponse;
import com.school.module.stat.dto.GradeStatResponse;
import com.school.module.stat.dto.StudentTrendResponse;
import com.school.module.stat.service.StatService;
import com.school.module.subject.entity.Subject;
import com.school.module.subject.mapper.SubjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChartTools {

    private static final BigDecimal DEFAULT_FULL_SCORE = BigDecimal.valueOf(100);
    // 分段阈值按得分率：<60% / 60-70% / 70-80% / 80-90% / 90-100%，与 StatServiceImpl.calculateScoreRanges 同口径
    private static final BigDecimal[] RANGE_RATIOS = {
            new BigDecimal("0.6"), new BigDecimal("0.7"), new BigDecimal("0.8"), new BigDecimal("0.9")};

    private final ScoreService scoreService;
    private final StatService statService;
    private final AiPermissionChecker permissionChecker;
    private final ObjectMapper objectMapper;
    // ponytail: D-ai-4/D-ai-6 取满分与科目名只需两个 Mapper 的主键/考试维度查询，不再借道 StatService 的全校聚合
    private final ExamSubjectMapper examSubjectMapper;
    private final SubjectMapper subjectMapper;

    @Tool(description = """
            生成班级各科平均分柱状图（ECharts配置JSON）。
            前端收到后会自动渲染为图表。
            """)
    public String generateClassSubjectBarChart(
            @ToolParam(description = "考试ID") Long examId,
            @ToolParam(description = "班级ID") Long classId,
            ToolContext toolContext) {
        permissionChecker.assertCanViewClass(toolContext, classId);
        ClassStatResponse stat = statService.getClassStat(examId, classId);

        List<String> subjects = stat.getSubjectStats().stream()
                .map(ClassStatResponse.SubjectStat::getSubjectName).collect(Collectors.toList());
        List<Object> avgScores = stat.getSubjectStats().stream()
                .map(s -> (Object) s.getAverageScore()).collect(Collectors.toList());
        List<Object> passRates = stat.getSubjectStats().stream()
                .map(s -> (Object) s.getPassRate()).collect(Collectors.toList());

        Map<String, Object> option = new LinkedHashMap<>();
        option.put("__chart__", true);
        option.put("title", Map.of("text",
                (stat.getClassName() != null ? stat.getClassName() : "班级")
                + " - " + (stat.getExamName() != null ? stat.getExamName() : "考试")
                + " 各科统计"));
        option.put("tooltip", Map.of("trigger", "axis"));
        option.put("legend", Map.of("data", List.of("平均分", "及格率(%)")));
        option.put("xAxis", Map.of("type", "category", "data", subjects));
        option.put("yAxis", Map.of("type", "value"));
        option.put("series", List.of(
                Map.of("name", "平均分", "type", "bar", "data", avgScores),
                Map.of("name", "及格率(%)", "type", "bar", "data", passRates)
        ));
        return toJson(option);
    }

    @Tool(description = """
            生成学生历次考试总分折线趋势图（ECharts配置JSON）。
            适合分析学生成绩进步或退步趋势。
            """)
    public String generateStudentTrendLineChart(
            @ToolParam(description = "学生ID") Long studentId,
            @ToolParam(description = "科目ID，可选，不传则展示总分趋势") Long subjectId,
            ToolContext toolContext) {
        permissionChecker.assertCanViewStudent(toolContext, studentId);
        StudentTrendResponse trend = statService.getStudentTrend(studentId, subjectId, null);

        List<String> examNames = trend.getExamTrends().stream()
                .map(StudentTrendResponse.ExamTrend::getExamName).collect(Collectors.toList());
        List<Object> scores = trend.getExamTrends().stream()
                .map(t -> (Object) t.getTotalScore()).collect(Collectors.toList());

        Map<String, Object> option = new LinkedHashMap<>();
        option.put("__chart__", true);
        option.put("title", Map.of("text",
                (trend.getStudentName() != null ? trend.getStudentName() : "学生") + " 成绩趋势"));
        option.put("tooltip", Map.of("trigger", "axis"));
        option.put("xAxis", Map.of("type", "category", "data", examNames));
        option.put("yAxis", Map.of("type", "value"));
        option.put("series", List.of(
                Map.of("type", "line", "data", scores, "smooth", true,
                        "markPoint", Map.of("data", List.of(
                                Map.of("type", "max", "name", "最高"),
                                Map.of("type", "min", "name", "最低")
                        )))
        ));
        return toJson(option);
    }

    @Tool(description = """
            生成班级成绩分数段分布饼图（ECharts配置JSON）。
            展示各分数段的人数占比：按满分折算得分率分为 <60%、60-70%、70-80%、80-90%、90-100% 五段，
            图例标注的是实际分值区间（如满分 150 时为 0-90、90-105、…、135-150）。
            可指定科目ID生成单科分布，不传则生成总分分布。
            """)
    public String generateScoreDistributionPieChart(
            @ToolParam(description = "考试ID") Long examId,
            @ToolParam(description = "班级ID") Long classId,
            @ToolParam(description = "科目ID，可选，不传则显示总分分布") Long subjectId,
            ToolContext toolContext) {
        permissionChecker.assertCanViewClass(toolContext, classId);

        List<Map<String, Object>> pieData;
        String title;

        if (subjectId != null) {
            // 单科：取该班该科目明细，自己算分段，口径精确
            List<ClassScoreItemDto> scoreList = scoreService.getClassScoreList(examId, classId, subjectId);
            // ponytail: D-ai-6 之前仅为取科目名就调 statService.getSubjectStat（全校该科成绩加载 + 各班/年级聚合），改为 subjectMapper.selectById
            Subject subject = subjectMapper.selectById(subjectId);
            String subjectName = subject != null && subject.getSubjectName() != null
                    ? subject.getSubjectName() : "科目" + subjectId;
            // ponytail: D-ai-4 单科分段不再写死百分制，按满分（exam_subject.full_score 优先，subject.full_score 兜底，再 100）折算得分率分段
            BigDecimal fullScore = resolveFullScore(examId, subjectId, subject);
            title = subjectName + " 分数段分布（满分 " + plain(fullScore) + "）";
            pieData = calcRanges(scoreList.stream()
                    .filter(s -> s.getScore() != null && !Boolean.TRUE.equals(s.getIsAbsent()))
                    .map(ClassScoreItemDto::getScore)
                    .collect(Collectors.toList()), fullScore);
        } else {
            // 总分：从 ClassStatResponse 取（已按班级聚合）
            ClassStatResponse stat = statService.getClassStat(examId, classId);
            title = (stat.getClassName() != null ? stat.getClassName() : "班级") + " 总分分布";
            pieData = stat.getScoreRanges().stream()
                    .map(r -> Map.<String, Object>of("name", r.getRange(), "value", r.getCount()))
                    .collect(Collectors.toList());
        }

        Map<String, Object> option = new LinkedHashMap<>();
        option.put("__chart__", true);
        option.put("title", Map.of("text", title));
        option.put("tooltip", Map.of("trigger", "item", "formatter", "{b}: {c}人 ({d}%)"));
        option.put("series", List.of(Map.of("type", "pie", "radius", "60%", "data", pieData)));
        return toJson(option);
    }

    /**
     * 单科满分：exam_subject.full_score（该场考试可单独设定）优先，其次 subject.full_score，最后兜底 100
     */
    private BigDecimal resolveFullScore(Long examId, Long subjectId, Subject subject) {
        List<ExamSubject> examSubjects = examSubjectMapper.selectByExamId(examId);
        BigDecimal examFullScore = examSubjects == null ? null : examSubjects.stream()
                .filter(es -> subjectId.equals(es.getSubjectId()))
                .map(ExamSubject::getFullScore)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        if (examFullScore != null && examFullScore.signum() > 0) {
            return examFullScore;
        }
        if (subject != null && subject.getFullScore() != null && subject.getFullScore().signum() > 0) {
            return subject.getFullScore();
        }
        return DEFAULT_FULL_SCORE;
    }

    /**
     * 按得分率分段（分数 < 满分×60% / <70% / <80% / <90% / 其余），name 标注实际分值区间
     */
    private List<Map<String, Object>> calcRanges(List<BigDecimal> scores, BigDecimal fullScore) {
        BigDecimal[] bounds = new BigDecimal[RANGE_RATIOS.length];
        for (int i = 0; i < RANGE_RATIOS.length; i++) {
            bounds[i] = fullScore.multiply(RANGE_RATIOS[i]).setScale(1, RoundingMode.HALF_UP);
        }
        int[] counts = new int[bounds.length + 1];
        for (BigDecimal score : scores) {
            int bucket = 0;
            while (bucket < bounds.length && score.compareTo(bounds[bucket]) >= 0) {
                bucket++;
            }
            counts[bucket]++;
        }
        List<Map<String, Object>> data = new ArrayList<>();
        BigDecimal lower = BigDecimal.ZERO;
        for (int i = 0; i <= bounds.length; i++) {
            BigDecimal upper = i < bounds.length ? bounds[i] : fullScore;
            data.add(Map.<String, Object>of("name", plain(lower) + "-" + plain(upper), "value", counts[i]));
            lower = upper;
        }
        return data;
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    @Tool(description = """
            生成年级各班成绩对比柱状图（ECharts配置JSON）。
            横向对比同一次考试中各班级的平均分。仅管理员可用。
            """)
    public String generateGradeClassCompareChart(
            @ToolParam(description = "考试ID") Long examId,
            @ToolParam(description = "年级ID，可选") Long gradeId,
            ToolContext toolContext) {
        permissionChecker.assertIsAdmin(toolContext);
        GradeStatResponse gradeStat = statService.getGradeStat(examId, gradeId);

        List<String> classNames = gradeStat.getClassComparisons().stream()
                .map(GradeStatResponse.ClassComparison::getClassName).collect(Collectors.toList());
        List<Object> avgScores = gradeStat.getClassComparisons().stream()
                .map(c -> (Object) c.getAverageScore()).collect(Collectors.toList());

        Map<String, Object> option = new LinkedHashMap<>();
        option.put("__chart__", true);
        option.put("title", Map.of("text",
                (gradeStat.getGradeName() != null ? gradeStat.getGradeName() : "年级") + " 各班平均分对比"));
        option.put("tooltip", Map.of("trigger", "axis"));
        option.put("xAxis", Map.of("type", "category", "data", classNames));
        option.put("yAxis", Map.of("type", "value"));
        option.put("series", List.of(Map.of("name", "平均分", "type", "bar", "data", avgScores)));
        return toJson(option);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("生成图表JSON失败", e);
            return "{}";
        }
    }

}
