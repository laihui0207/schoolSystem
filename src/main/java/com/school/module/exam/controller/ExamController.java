package com.school.module.exam.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.common.result.Result;
import com.school.module.exam.dto.ExamCreateRequest;
import com.school.module.exam.dto.ExamProgressResponse;
import com.school.module.exam.dto.ExamQueryRequest;
import com.school.module.exam.dto.ExamResponse;
import com.school.module.exam.dto.ExamUpdateRequest;
import com.school.module.exam.service.ExamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 考试管理控制器
 *
 * @author School System
 */
@RestController
@RequestMapping("/api/exams")
@RequiredArgsConstructor
public class ExamController {

    private final ExamService examService;

    /**
     * 创建考试
     */
    @PostMapping
    public Result<ExamResponse> createExam(@Valid @RequestBody ExamCreateRequest request) {
        ExamResponse response = examService.createExam(request);
        return Result.success(response);
    }

    /**
     * 查询考试详情
     */
    @GetMapping("/{id}")
    public Result<ExamResponse> getExamById(@PathVariable Long id) {
        ExamResponse response = examService.getExamById(id);
        return Result.success(response);
    }

    /**
     * 更新考试信息
     */
    @PutMapping("/{id}")
    public Result<ExamResponse> updateExam(
            @PathVariable Long id,
            @Valid @RequestBody ExamUpdateRequest request) {
        ExamResponse response = examService.updateExam(id, request);
        return Result.success(response);
    }

    /**
     * 更新考试状态
     */
    @PatchMapping("/{id}/status")
    public Result<Void> updateExamStatus(
            @PathVariable Long id,
            @RequestParam Integer status) {
        examService.updateExamStatus(id, status);
        return Result.success();
    }

    /**
     * 删除考试
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteExam(@PathVariable Long id) {
        examService.deleteExam(id);
        return Result.success();
    }

    /**
     * 分页查询考试列表
     */
    @GetMapping
    public Result<Page<ExamResponse>> listExams(ExamQueryRequest request) {
        Page<ExamResponse> page = examService.listExams(request);
        return Result.success(page);
    }

    /**
     * 开始成绩录入
     */
    @PostMapping("/{id}/start-input")
    public Result<Void> startScoreInput(@PathVariable Long id) {
        examService.startScoreInput(id);
        return Result.success();
    }

    /**
     * 提交审核
     */
    @PostMapping("/{id}/submit-review")
    public Result<Void> submitForReview(@PathVariable Long id) {
        examService.submitForReview(id);
        return Result.success();
    }

    /**
     * 发布考试成绩
     */
    @PostMapping("/{id}/publish")
    public Result<Void> publishExam(@PathVariable Long id) {
        examService.publishExam(id);
        return Result.success();
    }

    @GetMapping("/{id}/progress")
    public Result<ExamProgressResponse> getExamProgress(@PathVariable Long id) {
        return Result.success(examService.getExamProgress(id));
    }

}
