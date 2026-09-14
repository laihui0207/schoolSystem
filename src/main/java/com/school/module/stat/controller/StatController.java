package com.school.module.stat.controller;

import com.school.common.result.Result;
import com.school.module.stat.dto.ClassStatResponse;
import com.school.module.stat.dto.GradeStatResponse;
import com.school.module.stat.dto.PersonalStatResponse;
import com.school.module.stat.dto.StudentTrendResponse;
import com.school.module.stat.service.StatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 成绩统计控制器
 *
 * @author School System
 */
@RestController
@RequestMapping("/api/stats")
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
            @RequestParam(required = false) Long subjectId) {
        StudentTrendResponse response = statService.getStudentTrend(studentId, subjectId);
        return Result.success(response);
    }

    /**
     * 获取班级成绩统计
     */
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
    @GetMapping("/grade")
    public Result<GradeStatResponse> getGradeStat(
            @RequestParam Long examId,
            @RequestParam Long gradeId) {
        GradeStatResponse response = statService.getGradeStat(examId, gradeId);
        return Result.success(response);
    }

}
