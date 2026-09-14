package com.school.ai.tools;

import com.school.module.score.dto.ClassScoreItemDto;
import com.school.module.score.dto.ScoreStatisticsDto;
import com.school.module.score.service.ScoreService;
import com.school.module.stat.dto.ClassStatResponse;
import com.school.module.stat.dto.PersonalStatResponse;
import com.school.module.stat.dto.StudentTrendResponse;
import com.school.module.stat.dto.SubjectStatResponse;
import com.school.module.stat.service.StatService;
import com.school.module.student.dto.StudentResponse;
import com.school.module.student.service.StudentService;
import com.school.security.model.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class SchoolDataTools {

    private final ScoreService scoreService;
    private final StatService statService;
    private final StudentService studentService;
    private final AiPermissionChecker permissionChecker;

    @Tool(description = "查询指定考试某班级的成绩列表，可按科目筛选。返回学生姓名、分数、排名等。")
    public List<ClassScoreItemDto> queryClassScores(
            @ToolParam(description = "考试ID") Long examId,
            @ToolParam(description = "班级ID") Long classId,
            @ToolParam(description = "科目ID，可选，不传则返回所有科目") Long subjectId) {
        permissionChecker.assertCanViewClass(classId);
        return scoreService.getClassScoreList(examId, classId, subjectId);
    }

    @Tool(description = "获取班级考试统计：平均分、最高分、最低分、及格率、优秀率、各科目统计、分数段分布。")
    public ClassStatResponse getClassStat(
            @ToolParam(description = "考试ID") Long examId,
            @ToolParam(description = "班级ID") Long classId) {
        permissionChecker.assertCanViewClass(classId);
        return statService.getClassStat(examId, classId);
    }

    @Tool(description = "获取学生在某次考试的个人成绩统计，包含各科分数、班级排名、年级排名。")
    public PersonalStatResponse getPersonalStat(
            @ToolParam(description = "考试ID") Long examId,
            @ToolParam(description = "学生ID，学生角色不传则默认当前用户") Long studentId) {
        Long targetStudentId = resolveStudentId(studentId);
        permissionChecker.assertCanViewStudent(targetStudentId);
        return statService.getPersonalStat(examId, targetStudentId);
    }

    @Tool(description = "获取学生历次考试成绩趋势，含每次总分、排名变化、各科目雷达图数据。可按科目或学期筛选。")
    public StudentTrendResponse getStudentTrend(
            @ToolParam(description = "学生ID，学生角色不传则默认当前用户") Long studentId,
            @ToolParam(description = "科目ID，可选") Long subjectId,
            @ToolParam(description = "学期ID，可选") Long termId) {
        Long targetStudentId = resolveStudentId(studentId);
        permissionChecker.assertCanViewStudent(targetStudentId);
        return statService.getStudentTrend(targetStudentId, subjectId, termId);
    }

    @Tool(description = "获取班级某次考试的成绩统计摘要：总人数、录入人数、平均分、最高分、最低分、及格率、优秀率。")
    public ScoreStatisticsDto getScoreStatistics(
            @ToolParam(description = "考试ID") Long examId,
            @ToolParam(description = "班级ID") Long classId,
            @ToolParam(description = "科目ID，可选") Long subjectId) {
        permissionChecker.assertCanViewClass(classId);
        return scoreService.getScoreStatistics(examId, classId, subjectId);
    }

    @Tool(description = "查询班级的学生列表，返回学生姓名、学号、状态等基本信息。")
    public List<StudentResponse> getStudentsByClass(
            @ToolParam(description = "班级ID") Long classId) {
        permissionChecker.assertCanViewClass(classId);
        return studentService.getStudentsByClassId(classId);
    }

    @Tool(description = """
            获取某次考试单科的全校统计：平均分、及格率、优秀率、标准差、各班平均分、年级箱线图、分数段分布。
            适合回答"全校数学平均分多少"、"语文各班对比"等单科维度问题。
            """)
    public SubjectStatResponse getSubjectStat(
            @ToolParam(description = "考试ID") Long examId,
            @ToolParam(description = "科目ID") Long subjectId) {
        return statService.getSubjectStat(examId, subjectId);
    }

    private Long resolveStudentId(Long studentId) {
        if (studentId != null) return studentId;
        LoginUser user = permissionChecker.currentUser();
        if (user.isStudent()) return user.getStudentId();
        throw new com.school.common.exception.BusinessException(
                com.school.common.result.ResultCode.PARAM_ERROR, "请指定学生ID");
    }
}
