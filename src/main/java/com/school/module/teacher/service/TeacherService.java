package com.school.module.teacher.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.module.teacher.dto.TeacherCreateRequest;
import com.school.module.teacher.dto.TeacherQueryRequest;
import com.school.module.teacher.dto.TeacherResponse;
import com.school.module.teacher.dto.TeacherUpdateRequest;

/**
 * 教师服务接口
 *
 * @author School System
 */
public interface TeacherService {

    /**
     * 创建教师
     *
     * @param request 创建请求
     * @return 教师响应
     */
    TeacherResponse createTeacher(TeacherCreateRequest request);

    /**
     * 根据ID查询教师详情
     *
     * @param id 教师ID
     * @return 教师响应
     */
    TeacherResponse getTeacherById(Long id);

    /**
     * 更新教师信息
     *
     * @param id 教师ID
     * @param request 更新请求
     * @return 教师响应
     */
    TeacherResponse updateTeacher(Long id, TeacherUpdateRequest request);

    /**
     * 更新教师状态
     *
     * @param id 教师ID
     * @param status 状态：0-离职，1-在职
     */
    void updateTeacherStatus(Long id, Integer status);

    /**
     * 设置主教科目
     *
     * @param id 教师ID
     * @param subjectId 科目ID
     */
    void setSubject(Long id, Long subjectId);

    /**
     * 删除教师
     *
     * @param id 教师ID
     */
    void deleteTeacher(Long id);

    /**
     * 分页查询教师列表
     *
     * @param request 查询请求
     * @return 分页结果
     */
    Page<TeacherResponse> listTeachers(TeacherQueryRequest request);

}
