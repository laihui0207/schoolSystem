package com.school.module.student.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.module.student.dto.*;

import java.util.List;

/**
 * 学生服务接口
 *
 * @author School System
 */
public interface StudentService {

    /**
     * 创建学生
     *
     * @param request 创建请求
     * @return 学生响应
     */
    StudentResponse createStudent(StudentCreateRequest request);

    /**
     * 根据ID查询学生详情
     *
     * @param id 学生ID
     * @return 学生响应
     */
    StudentResponse getStudentById(Long id);

    /**
     * 更新学生信息
     *
     * @param id 学生ID
     * @param request 更新请求
     * @return 学生响应
     */
    StudentResponse updateStudent(Long id, StudentUpdateRequest request);

    /**
     * 更新学生状态
     *
     * @param id 学生ID
     * @param status 状态：0-离校，1-在读，2-休学
     */
    void updateStudentStatus(Long id, Integer status);

    /**
     * 删除学生
     *
     * @param id 学生ID
     */
    void deleteStudent(Long id);

    /**
     * 分页查询学生列表
     *
     * @param request 查询请求
     * @return 分页结果
     */
    Page<StudentResponse> listStudents(StudentQueryRequest request);

    /**
     * 学生分班
     *
     * @param studentId 学生ID
     * @param classId 班级ID
     */
    void assignToClass(Long studentId, Long classId);

    /**
     * 学生转班
     *
     * @param studentId 学生ID
     * @param fromClassId 原班级ID
     * @param toClassId 目标班级ID
     */
    void transferClass(Long studentId, Long fromClassId, Long toClassId);

    /**
     * 移除学生的班级
     *
     * @param studentId 学生ID
     * @param classId 班级ID
     */
    void removeFromClass(Long studentId, Long classId);

    /**
     * 查询班级下的所有学生
     *
     * @param classId 班级ID
     * @return 学生列表
     */
    List<StudentResponse> getStudentsByClassId(Long classId);

}
