package com.school.module.stat.service;

import com.school.module.stat.dto.ClassStatResponse;
import com.school.module.stat.dto.GradeStatResponse;
import com.school.module.stat.dto.PersonalStatResponse;
import com.school.module.stat.dto.StudentTrendResponse;
import com.school.module.stat.dto.SubjectStatResponse;

/**
 * 成绩统计服务接口
 *
 * @author School System
 */
public interface StatService {

    /**
     * 获取学生个人成绩统计
     *
     * @param examId 考试ID
     * @param studentId 学生ID
     * @return 个人成绩统计
     */
    PersonalStatResponse getPersonalStat(Long examId, Long studentId);

    /**
     * 获取学生成绩趋势
     *
     * @param studentId 学生ID
     * @param subjectId 科目ID（可选，为空则返回总分趋势）
     * @return 成绩趋势
     */
    StudentTrendResponse getStudentTrend(Long studentId, Long subjectId);

    StudentTrendResponse getStudentTrend(Long studentId, Long subjectId, Long termId);

    /**
     * 获取班级成绩统计
     *
     * @param examId 考试ID
     * @param classId 班级ID
     * @return 班级成绩统计
     */
    ClassStatResponse getClassStat(Long examId, Long classId);

    /**
     * 获取年级成绩统计
     *
     * @param examId 考试ID
     * @param gradeId 年级ID
     * @return 年级成绩统计
     */
    GradeStatResponse getGradeStat(Long examId, Long gradeId);

    SubjectStatResponse getSubjectStat(Long examId, Long subjectId);

}
