package com.school.module.score.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.module.score.dto.*;

import java.util.List;

/**
 * 成绩服务接口
 *
 * @author School System
 */
public interface ScoreService {

    /**
     * 单个成绩录入
     *
     * @param request 录入请求
     * @return 成绩响应
     */
    ScoreResponse inputScore(ScoreInputRequest request);

    /**
     * 批量成绩录入
     *
     * @param request 批量录入请求
     * @return 成功录入的数量
     */
    Integer batchInputScores(ScoreBatchInputRequest request);

    /**
     * 查询成绩详情
     *
     * @param id 成绩ID
     * @return 成绩响应
     */
    ScoreResponse getScoreById(Long id);

    /**
     * 分页查询成绩列表
     *
     * @param request 查询请求
     * @return 分页结果
     */
    Page<ScoreResponse> listScores(ScoreQueryRequest request);

    /**
     * 查询学生某次考试的所有成绩
     *
     * @param examId 考试ID
     * @param studentId 学生ID
     * @return 成绩列表
     */
    List<ScoreResponse> getStudentExamScores(Long examId, Long studentId);

    /**
     * 审核成绩
     *
     * @param request 审核请求
     */
    void reviewScores(ScoreReviewRequest request);

    /**
     * 计算排名
     *
     * @param examId 考试ID
     * @param subjectId 科目ID（可选，为空则计算总分排名）
     */
    void calculateRankings(Long examId, Long subjectId);

    /**
     * 删除成绩
     *
     * @param id 成绩ID
     */
    void deleteScore(Long id);

    List<ClassScoreItemDto> getClassScoreList(Long examId, Long classId, Long subjectId);

    ScoreStatisticsDto getScoreStatistics(Long examId, Long classId, Long subjectId);

    List<ReviewListItemDto> getReviewList(Long examId, Long classId, Long subjectId, Long schoolId);

    ReviewDetailDto getReviewDetail(Long examId, Long classId, Long subjectId);

    List<ReviewHistoryDto> getReviewHistory(Long examId, Long classId, Long subjectId);

    List<ScoreDistributionDto> getScoreDistribution(Long examId, Long classId, Long subjectId);

}
