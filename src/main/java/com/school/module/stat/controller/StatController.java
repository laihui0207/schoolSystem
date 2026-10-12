package com.school.module.stat.controller;

import com.school.common.result.Result;
import com.school.module.stat.dto.ClassStatResponse;
import com.school.module.stat.dto.GradeStatResponse;
import com.school.module.stat.dto.PersonalStatResponse;
import com.school.module.stat.dto.RegionOptionResponse;
import com.school.module.stat.dto.RegionStatQuery;
import com.school.module.stat.dto.RegionStatResponse;
import com.school.module.stat.dto.StudentTrendResponse;
import com.school.module.stat.dto.SubjectStatResponse;
import com.school.module.stat.service.StatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 成绩统计控制器
 *
 * @author School System
 */
@RestController
@RequestMapping("/stats")
@RequiredArgsConstructor
public class StatController {

    private final StatService statService;

    /**
     * 获取学生个人成绩统计
     */
    @GetMapping("/personal")
    public Result<PersonalStatResponse> getPersonalStat(
            @RequestParam Long examId,
            @RequestParam Long studentId) {
        PersonalStatResponse response = statService.getPersonalStat(examId, studentId);
        return Result.success(response);
    }

    /**
     * 获取学生成绩趋势
     */
    @GetMapping("/student/{studentId}/trend")
    public Result<StudentTrendResponse> getStudentTrend(
            @PathVariable Long studentId,
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) Long termId) {
        // ponytail: 契约 3 —— termId 可选，前端按学期筛趋势；Service 已有三参重载，直接透传
        StudentTrendResponse response = statService.getStudentTrend(studentId, subjectId, termId);
        return Result.success(response);
    }

    /**
     * 获取班级成绩统计
     */
    // ponytail: 契约 S —— 班级/年级/科目聚合仅管理员与教师可见，学生只有 /personal 与 /student/{id}/trend
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN', 'TEACHER')")
    @GetMapping("/class")
    public Result<ClassStatResponse> getClassStat(
            @RequestParam Long examId,
            @RequestParam Long classId) {
        ClassStatResponse response = statService.getClassStat(examId, classId);
        return Result.success(response);
    }

    /**
     * 获取年级成绩统计
     */
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN', 'TEACHER')")
    @GetMapping("/grade")
    public Result<GradeStatResponse> getGradeStat(
            @RequestParam Long examId,
            @RequestParam(required = false) Long gradeId) {
        // ponytail: 契约 3 —— gradeId 可选；省略时返回本校整场考试总览，返回结构不变
        GradeStatResponse response = statService.getGradeStat(examId, gradeId);
        return Result.success(response);
    }

    /**
     * 获取科目成绩统计（某次考试的单科维度）
     */
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN', 'TEACHER')")
    @GetMapping("/subject")
    public Result<SubjectStatResponse> getSubjectStat(
            @RequestParam Long examId,
            @RequestParam Long subjectId) {
        // ponytail: 契约 3 —— Service 已实现 getSubjectStat 但此前没有端点暴露，前端科目分析页 404
        SubjectStatResponse response = statService.getSubjectStat(examId, subjectId);
        return Result.success(response);
    }

    /**
     * 区域（跨校）成绩对比
     * <p>
     * 同一 region_code 下的学校，按 (year, term, examType) 各取 exam_date 最新一场已发布考试，聚合后按均分排名。
     */
    // ponytail: 跨校数据只有超管可见——校管/教师的租户上下文只有一所学校，没有「区域」概念；
    // 查询参数是 GET 对象绑定，@Valid 失败走 BindException → 400
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @GetMapping("/region")
    public Result<RegionStatResponse> getRegionStat(@Valid RegionStatQuery query) {
        RegionStatResponse response = statService.getRegionStat(query);
        return Result.success(response);
    }

    /**
     * 已配置区域编码的区域列表（供下拉）
     */
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @GetMapping("/regions")
    public Result<List<RegionOptionResponse>> listRegions() {
        return Result.success(statService.listRegions());
    }

}
