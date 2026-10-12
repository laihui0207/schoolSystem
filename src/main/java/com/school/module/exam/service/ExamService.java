package com.school.module.exam.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.module.exam.dto.ExamCreateRequest;
import com.school.module.exam.dto.ExamProgressResponse;
import com.school.module.exam.dto.ExamQueryRequest;
import com.school.module.exam.dto.ExamResponse;
import com.school.module.exam.dto.ExamUpdateRequest;

/**
 * 考试服务接口
 *
 * @author School System
 */
public interface ExamService {

    /**
     * 创建考试
     *
     * @param request 创建请求
     * @return 考试响应
     */
    ExamResponse createExam(ExamCreateRequest request);

    /**
     * 根据ID查询考试详情
     *
     * @param id 考试ID
     * @return 考试响应
     */
    ExamResponse getExamById(Long id);

    /**
     * 更新考试信息
     *
     * @param id 考试ID
     * @param request 更新请求
     * @return 考试响应
     */
    ExamResponse updateExam(Long id, ExamUpdateRequest request);

    /**
     * 更新考试状态
     *
     * @param id 考试ID
     * @param status 状态：0-未开始，1-录入中，2-审核中，3-已发布
     */
    void updateExamStatus(Long id, Integer status);

    /**
     * 删除考试
     *
     * @param id 考试ID
     */
    void deleteExam(Long id);

    /**
     * 分页查询考试列表
     *
     * @param request 查询请求
     * @return 分页结果
     */
    Page<ExamResponse> listExams(ExamQueryRequest request);

    /**
     * 开始成绩录入
     *
     * @param id 考试ID
     */
    void startScoreInput(Long id);

    /**
     * 提交审核
     *
     * @param id 考试ID
     */
    void submitForReview(Long id);

    /**
     * 发布考试成绩
     *
     * @param id 考试ID
     */
    void publishExam(Long id);

    /**
     * 获取考试录入进度
     *
     * @param id 考试ID
     * @return 各班级各科目录入进度
     */
    ExamProgressResponse getExamProgress(Long id);

}
