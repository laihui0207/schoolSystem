package com.school.ai.tools;

import com.school.module.stat.dto.ClassStatResponse;
import com.school.module.stat.dto.GradeStatResponse;
import com.school.module.stat.dto.StudentTrendResponse;
import com.school.module.stat.service.StatService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI 图表生成工具
 * 返回 ECharts option JSON 字符串，前端识别后直接渲染
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChartTools {

    private final StatService statService;
    private final AiPermissionChecker permissionChecker;
    private final ObjectMapper objectMapper;

    @Tool(description = """
            生成班级各科平均分柱状图（ECharts配置JSON）。
            前端收到后会自动渲染为图表，无需用户额外操作。
            """)
    public String generateClassSubjectBarChart(
            @ToolParam(description = "考试ID") Long examId,
            @ToolParam(description = "班级ID") Long classId) {
        permissionChecker.assertCanViewClass(classId);
        ClassStatResponse stat = statService.getClassStat(examId, classId);

        List<String> subjects = stat.getSubjectStats().stream()
                .map(ClassStatResponse.SubjectStat::getSubjectName)
                .collect(Collectors.toList());
        List<Object> avgScores = stat.getSubjectStats().stream()
                .map(s -> s.getAverageScore())
                .collect(Collectors.toList());
        List<Object> passRates = stat.getSubjectStats().stream()
                .map(s -> s.getPassRate())
                .collect(Collectors.toList());

        Map<String, Object> option = new LinkedHashMap<>();
        option.put("__chart__", true);
        option.put("title", Map.of("text", stat.getClassName() + " - " + stat.getExamName() + " 各科统计"));
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
            @ToolParam(description = "科目ID，可选，不传则展示总分趋势") Long subjectId) {
        permissionChecker.assertCanViewStudent(studentId);
        StudentTrendResponse trend = statService.getStudentTrend(studentId, subjectId, null);

        List<String> examNames = trend.getExamTrends().stream()
                .map(StudentTrendResponse.ExamTrend::getExamName)
                .collect(Collectors.toList());
        List<Object> scores = trend.getExamTrends().stream()
                .map(t -> subjectId != null && t.getSubjectScores() != null
                        ? t.getSubjectScores().values().stream().findFirst().orElse(t.getTotalScore())
                        : t.getTotalScore())
                .collect(Collectors.toList());

        Map<String, Object> option = new LinkedHashMap<>();
        option.put("__chart__", true);
        option.put("title", Map.of("text", trend.getStudentName() + " 成绩趋势"));
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
            展示各分数段（0-60,60-70,70-80,80-90,90-100）的人数占比。
            """)
    public String generateScoreDistributionPieChart(
            @ToolParam(description = "考试ID") Long examId,
            @ToolParam(description = "班级ID") Long classId) {
        permissionChecker.assertCanViewClass(classId);
        ClassStatResponse stat = statService.getClassStat(examId, classId);

        List<Map<String, Object>> pieData = stat.getScoreRanges().stream()
                .map(r -> Map.<String, Object>of("name", r.getRange(), "value", r.getCount()))
                .collect(Collectors.toList());

        Map<String, Object> option = new LinkedHashMap<>();
        option.put("__chart__", true);
        option.put("title", Map.of("text", stat.getClassName() + " 成绩分布"));
        option.put("tooltip", Map.of("trigger", "item", "formatter", "{b}: {c}人 ({d}%)"));
        option.put("series", List.of(
                Map.of("type", "pie", "radius", "60%", "data", pieData)
        ));

        return toJson(option);
    }

    @Tool(description = """
            生成年级各班成绩对比柱状图（ECharts配置JSON）。
            横向对比同一次考试中各班级的平均分和及格率。仅管理员可用。
            """)
    public String generateGradeClassCompareChart(
            @ToolParam(description = "考试ID") Long examId,
            @ToolParam(description = "年级ID，可选") Long gradeId) {
        permissionChecker.assertIsAdmin();
        GradeStatResponse gradeStat = statService.getGradeStat(examId, gradeId);

        List<String> classNames = gradeStat.getClassStats().stream()
                .map(GradeStatResponse.ClassComparison::getClassName)
                .collect(Collectors.toList());
        List<Object> avgScores = gradeStat.getClassStats().stream()
                .map(c -> c.getAverageScore())
                .collect(Collectors.toList());

        Map<String, Object> option = new LinkedHashMap<>();
        option.put("__chart__", true);
        option.put("title", Map.of("text", gradeStat.getGradeName() + " 各班平均分对比"));
        option.put("tooltip", Map.of("trigger", "axis"));
        option.put("xAxis", Map.of("type", "category", "data", classNames));
        option.put("yAxis", Map.of("type", "value"));
        option.put("series", List.of(
                Map.of("name", "平均分", "type", "bar", "data", avgScores)
        ));

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
