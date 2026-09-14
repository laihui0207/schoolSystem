package com.school.module.warning.service;

import java.util.Map;

/**
 * 成绩预警服务接口
 *
 * @author School System
 */
public interface ScoreWarningService {

    /**
     * 检查考试成绩预警
     *
     * @param examId 考试ID
     */
    void checkScoreWarnings(Long examId);

    /**
     * 检查单个学生成绩下降预警
     *
     * @param studentId 学生ID
     * @param examId 考试ID
     * @param subjectId 科目ID
     */
    void checkScoreDropWarning(Long studentId, Long examId, Long subjectId);

    /**
     * 分析学生成绩趋势
     *
     * @param studentId 学生ID
     * @param subjectId 科目ID（可选）
     * @return 趋势分析结果
     */
    Map<String, Object> analyzeScoreTrend(Long studentId, Long subjectId);

}
