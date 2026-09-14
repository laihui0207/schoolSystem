package com.school.module.score.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.common.result.Result;
import com.school.module.score.dto.*;
import com.school.module.score.service.ScoreService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 成绩管理控制器
 *
 * @author School System
 */
@RestController
@RequestMapping("/api/scores")
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
    public Result<Integer> batchInputScores(@Valid @RequestBody ScoreBatchInputRequest request) {
        Integer count = scoreService.batchInputScores(request);
        return Result.success("成功录入" + count + "条成绩", count);
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
    @PostMapping("/calculate-rankings")
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

    /** 班级成绩列表 */
    @GetMapping("/class-list")
    public Result<List<ClassScoreItemDto>> getClassScoreList(
            @RequestParam Long examId,
            @RequestParam Long classId,
            @RequestParam(required = false) Long subjectId) {
        return Result.success(scoreService.getClassScoreList(examId, classId, subjectId));
    }

    /** 成绩统计摘要 */
    @GetMapping("/statistics")
    public Result<ScoreStatisticsDto> getStatistics(
            @RequestParam Long examId,
            @RequestParam Long classId,
            @RequestParam(required = false) Long subjectId) {
        return Result.success(scoreService.getScoreStatistics(examId, classId, subjectId));
    }

    /** 审核列表 */
    @GetMapping("/review-list")
    public Result<List<ReviewListItemDto>> getReviewList(
            @RequestParam(required = false) Long examId,
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) Long schoolId) {
        return Result.success(scoreService.getReviewList(examId, classId, subjectId, schoolId));
    }

    /** 审核详情 */
    @GetMapping("/review-detail")
    public Result<ReviewDetailDto> getReviewDetail(
            @RequestParam Long examId,
            @RequestParam Long classId,
            @RequestParam(required = false) Long subjectId) {
        return Result.success(scoreService.getReviewDetail(examId, classId, subjectId));
    }

    /** 审核历史 */
    @GetMapping("/review-history")
    public Result<List<ReviewHistoryDto>> getReviewHistory(
            @RequestParam Long examId,
            @RequestParam Long classId,
            @RequestParam(required = false) Long subjectId) {
        return Result.success(scoreService.getReviewHistory(examId, classId, subjectId));
    }

    /** 分数段分布 */
    @GetMapping("/distribution")
    public Result<List<ScoreDistributionDto>> getDistribution(
            @RequestParam Long examId,
            @RequestParam Long classId,
            @RequestParam(required = false) Long subjectId) {
        return Result.success(scoreService.getScoreDistribution(examId, classId, subjectId));
    }

}
