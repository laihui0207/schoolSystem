package com.school.module.export.service;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.school.common.exception.BusinessException;
import com.school.common.result.ResultCode;
import com.school.module.class_module.entity.SchoolClass;
import com.school.module.class_module.mapper.ClassMapper;
import com.school.module.exam.entity.Exam;
import com.school.module.exam.mapper.ExamMapper;
import com.school.module.export.dto.StatOverviewExcel;
import com.school.module.export.dto.StatScoreRangeExcel;
import com.school.module.export.dto.StatSubjectExcel;
import com.school.module.stat.dto.ClassStatResponse;
import com.school.module.stat.dto.GradeStatResponse;
import com.school.module.stat.service.StatService;
import com.school.security.service.TeacherScopeService;
import com.school.security.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 统计报表 Excel 导出实现
 * <p>
 * 数据全部来自 StatService.getClassStat / getGradeStat，不再手算一套口径；
 * 权限与 ExportServiceImpl.exportScores 相同：学生拒绝、教师限班级、非管理员仅已发布。
 *
 * @author School System
 */
@Service
@RequiredArgsConstructor
public class StatReportExportServiceImpl implements StatReportExportService {

    private static final int EXAM_STATUS_PUBLISHED = 3;
    private static final String SCHOOL_TOTAL_LABEL = "全校合计";

    private final ExamMapper examMapper;
    private final ClassMapper classMapper;
    private final StatService statService;
    private final TeacherScopeService teacherScopeService;

    /**
     * 要出报表的班级（ID + 展示名）
     */
    private record ClassTarget(Long classId, String className) {
    }

    @Override
    public void exportStatReport(Long examId, Long classId, HttpServletResponse response) {
        Exam exam = examMapper.selectById(examId);
        if (exam == null) {
            throw new BusinessException(ResultCode.EXAM_NOT_FOUND, "考试不存在");
        }
        checkExportScope(exam, classId);

        // ponytail: 不指定班级时用 getGradeStat(examId, null) 的 classComparisons 作班级清单——它已按教师可见范围过滤、只含有成绩的班级、按均分名次排好
        GradeStatResponse overview = classId == null ? statService.getGradeStat(examId, null) : null;
        List<ClassTarget> targets = classId != null ? List.of(singleTarget(classId)) : targetsOf(overview);
        if (targets.isEmpty()) {
            throw new BusinessException("暂无成绩数据");
        }

        List<StatOverviewExcel> overviewRows = new ArrayList<>();
        List<StatSubjectExcel> subjectRows = new ArrayList<>();
        List<StatScoreRangeExcel> rangeRows = new ArrayList<>();
        for (ClassTarget target : targets) {
            ClassStatResponse stat = statService.getClassStat(examId, target.classId());
            overviewRows.add(overviewRow(target.className(), stat));
            subjectRows.addAll(subjectRows(target.className(), stat));
            rangeRows.addAll(rangeRows(target.className(), stat));
        }
        if (overview != null) {
            overviewRows.add(schoolTotalRow(overview));
        }

        write(response, exam, overviewRows, subjectRows, rangeRows);
    }

    // ---------------------------------------------------------------- 权限

    /**
     * ponytail: 与 ExportServiceImpl.checkExportScope 同口径（私有方法不跨类共享，改一处请同步另一处）：
     * 学生 FORBIDDEN；管理员不限；教师必须指定任教/班主任范围内的班级；非管理员只能导出已发布考试
     */
    private void checkExportScope(Exam exam, Long classId) {
        if (SecurityUtils.isStudent()) {
            throw new BusinessException(ResultCode.FORBIDDEN, "学生账号无权导出统计报表");
        }
        if (SecurityUtils.isSuperAdmin() || SecurityUtils.isSchoolAdmin()) {
            return;
        }
        if (!SecurityUtils.isTeacher()) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅管理员和教师可导出统计报表");
        }
        if (!Integer.valueOf(EXAM_STATUS_PUBLISHED).equals(exam.getStatus())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "考试尚未发布，暂不能导出统计报表");
        }
        if (classId == null) {
            throw new BusinessException(ResultCode.FORBIDDEN, "教师账号必须指定班级");
        }
        if (!teacherScopeService.currentAllowedClassIds().contains(classId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权导出该班级的统计报表");
        }
    }

    // ---------------------------------------------------------------- 班级清单

    private ClassTarget singleTarget(Long classId) {
        SchoolClass schoolClass = classMapper.selectById(classId);
        String name = schoolClass == null || schoolClass.getClassName() == null ? "班级" + classId : schoolClass.getClassName();
        return new ClassTarget(classId, name);
    }

    private static List<ClassTarget> targetsOf(GradeStatResponse overview) {
        if (overview == null || overview.getClassComparisons() == null) {
            return List.of();
        }
        return overview.getClassComparisons().stream()
                .filter(c -> c.getClassId() != null)
                .map(c -> new ClassTarget(c.getClassId(),
                        c.getClassName() == null ? "班级" + c.getClassId() : c.getClassName()))
                .collect(Collectors.toList());
    }

    // ---------------------------------------------------------------- 行转换

    private static StatOverviewExcel overviewRow(String className, ClassStatResponse stat) {
        return StatOverviewExcel.builder()
                .className(className)
                .totalStudents(stat.getTotalStudents())
                .actualStudents(stat.getActualStudents())
                .averageScore(stat.getAverageScore())
                .passRate(stat.getPassRate())
                .excellentRate(stat.getExcellentRate())
                .maxScore(stat.getMaxScore())
                .minScore(stat.getMinScore())
                .build();
    }

    private static StatOverviewExcel schoolTotalRow(GradeStatResponse overview) {
        // ponytail: GradeStatResponse.totalStudents 是「有有效成绩的学生数」（summarize 口径），人数与实考列写同一个值
        return StatOverviewExcel.builder()
                .className(SCHOOL_TOTAL_LABEL)
                .totalStudents(overview.getTotalStudents())
                .actualStudents(overview.getTotalStudents())
                .averageScore(overview.getAverageScore())
                .passRate(overview.getPassRate())
                .excellentRate(overview.getExcellentRate())
                .maxScore(overview.getMaxScore())
                .minScore(overview.getMinScore())
                .build();
    }

    private static List<StatSubjectExcel> subjectRows(String className, ClassStatResponse stat) {
        if (stat.getSubjectStats() == null) {
            return List.of();
        }
        return stat.getSubjectStats().stream()
                .map(s -> StatSubjectExcel.builder()
                        .className(className)
                        .subjectName(s.getSubjectName())
                        .averageScore(s.getAverageScore())
                        .passRate(s.getPassRate())
                        .excellentRate(s.getExcellentRate())
                        .maxScore(s.getMaxScore())
                        .minScore(s.getMinScore())
                        .build())
                .collect(Collectors.toList());
    }

    private static List<StatScoreRangeExcel> rangeRows(String className, ClassStatResponse stat) {
        if (stat.getScoreRanges() == null) {
            return List.of();
        }
        return stat.getScoreRanges().stream()
                .map(r -> StatScoreRangeExcel.builder()
                        .className(className)
                        .range(r.getRange())
                        .count(r.getCount())
                        .percentage(r.getPercentage())
                        .build())
                .collect(Collectors.toList());
    }

    // ---------------------------------------------------------------- 输出

    private void write(HttpServletResponse response, Exam exam,
                       List<StatOverviewExcel> overviewRows,
                       List<StatSubjectExcel> subjectRows,
                       List<StatScoreRangeExcel> rangeRows) {
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            String examName = exam.getExamName() == null ? String.valueOf(exam.getId()) : exam.getExamName();
            String fileName = URLEncoder.encode("统计报表_" + examName + "_" + System.currentTimeMillis(), StandardCharsets.UTF_8)
                    .replace("+", "%20");
            response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");

            // ponytail: 一个 ExcelWriter 写三个 sheet；数据已全部算好，finish 前不会再抛业务异常
            ExcelWriter writer = EasyExcel.write(response.getOutputStream()).build();
            try {
                writer.write(overviewRows, EasyExcel.writerSheet(0, "概览").head(StatOverviewExcel.class).build());
                writer.write(subjectRows, EasyExcel.writerSheet(1, "科目统计").head(StatSubjectExcel.class).build());
                writer.write(rangeRows, EasyExcel.writerSheet(2, "分数段").head(StatScoreRangeExcel.class).build());
            } finally {
                writer.finish();
            }
        } catch (IOException e) {
            throw new BusinessException("导出失败: " + e.getMessage());
        }
    }

}
