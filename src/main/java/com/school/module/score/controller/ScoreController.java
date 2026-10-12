package com.school.module.score.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.common.result.Result;
import com.school.module.score.dto.*;
import com.school.module.score.service.ScoreService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 成绩管理控制器
 *
 * @author School System
 */
@RestController
@RequestMapping("/scores")
@RequiredArgsConstructor
public class ScoreController {

    private final ScoreService scoreService;

    /**
     * 单个成绩录入
     */
    @PostMapping("/input")
    public Result<ScoreResponse> inputScore(@Valid @RequestBody ScoreInputRequest request) {
        ScoreResponse response = scoreService.inputScore(request);
        return Result.success(response);
    }

    /**
     * 批量成绩录入
     */
    @PostMapping("/batch-input")
    public Result<BatchInputResult> batchInputScores(@Valid @RequestBody ScoreBatchInputRequest request) {
        // ponytail: 契约 1——返回逐条失败明细而不是一个数字，前端才能提示具体哪位学生没录进去
        BatchInputResult result = scoreService.batchInputScores(request);
        return Result.success(result);
    }

    /**
     * 查询成绩详情
     */
    @GetMapping("/{id}")
    public Result<ScoreResponse> getScoreById(@PathVariable Long id) {
        ScoreResponse response = scoreService.getScoreById(id);
        return Result.success(response);
    }

    /**
     * 分页查询成绩列表
     */
    @GetMapping
    public Result<Page<ScoreResponse>> listScores(ScoreQueryRequest request) {
        Page<ScoreResponse> page = scoreService.listScores(request);
        return Result.success(page);
    }

    /**
     * 查询学生某次考试的所有成绩
     */
    @GetMapping("/student/{studentId}/exam/{examId}")
    public Result<List<ScoreResponse>> getStudentExamScores(
            @PathVariable Long examId,
            @PathVariable Long studentId) {
        List<ScoreResponse> scores = scoreService.getStudentExamScores(examId, studentId);
        return Result.success(scores);
    }

    /**
     * 审核成绩
     */
    @PostMapping("/review")
    public Result<Void> reviewScores(@Valid @RequestBody ScoreReviewRequest request) {
        scoreService.reviewScores(request);
        return Result.success();
    }

    /**
     * 计算排名
     */
    // ponytail: B-5——这是回写全场考试排名的写操作，学生/任课教师不得触发；与 publish 一样只给管理员
    @PostMapping("/calculate-rankings")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")
    public Result<Void> calculateRankings(
            @RequestParam Long examId,
            @RequestParam(required = false) Long subjectId) {
        scoreService.calculateRankings(examId, subjectId);
        return Result.success();
    }

    /**
     * 删除成绩
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteScore(@PathVariable Long id) {
        scoreService.deleteScore(id);
        return Result.success();
    }

    @GetMapping("/class-list")
    public Result<List<ClassScoreItemDto>> getClassScoreList(
            @RequestParam Long examId,
            @RequestParam Long classId,
            // ponytail: D-ai-5——科目可选，为空返回该班全部科目的成绩项
            @RequestParam(required = false) Long subjectId) {
        return Result.success(scoreService.getClassScoreList(examId, classId, subjectId));
    }

    @GetMapping("/statistics")
    public Result<ScoreStatisticsDto> getScoreStatistics(
            @RequestParam Long examId,
            @RequestParam Long classId,
            @RequestParam Long subjectId) {
        return Result.success(scoreService.getScoreStatistics(examId, classId, subjectId));
    }

    @GetMapping("/review-list")
    public Result<Page<ReviewListItemDto>> getReviewList(
            @RequestParam(required = false) Long examId,
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Long pageNum,
            @RequestParam(defaultValue = "10") Long pageSize) {
        return Result.success(scoreService.getReviewList(examId, classId, subjectId, status, pageNum, pageSize));
    }

    @GetMapping("/review-detail")
    public Result<ReviewDetailDto> getReviewDetail(
            @RequestParam Long examId,
            @RequestParam Long classId,
            @RequestParam Long subjectId) {
        return Result.success(scoreService.getReviewDetail(examId, classId, subjectId));
    }

    @GetMapping("/review-history")
    public Result<List<ReviewHistoryDto>> getReviewHistory(
            @RequestParam Long examId,
            @RequestParam Long classId,
            @RequestParam Long subjectId) {
        return Result.success(scoreService.getReviewHistory(examId, classId, subjectId));
    }

    @GetMapping("/distribution")
    public Result<List<ScoreDistributionDto>> getScoreDistribution(
            @RequestParam Long examId,
            @RequestParam Long classId,
            @RequestParam Long subjectId) {
        return Result.success(scoreService.getScoreDistribution(examId, classId, subjectId));
    }

}
