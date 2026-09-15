package com.school.module.class_module.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.module.class_module.dto.ClassCreateRequest;
import com.school.module.class_module.dto.ClassQueryRequest;
import com.school.module.class_module.dto.ClassResponse;
import com.school.module.class_module.dto.ClassUpdateRequest;

/**
 * 班级服务接口
 *
 * @author School System
 */
public interface ClassService {

    /**
     * 创建班级
     *
     * @param request 创建请求
     * @return 班级响应
     */
    ClassResponse createClass(ClassCreateRequest request);

    /**
     * 根据ID查询班级详情
     *
     * @param id 班级ID
     * @return 班级响应
     */
    ClassResponse getClassById(Long id);

    /**
     * 更新班级信息
     *
     * @param id 班级ID
     * @param request 更新请求
     * @return 班级响应
     */
    ClassResponse updateClass(Long id, ClassUpdateRequest request);

    /**
     * 设置班主任
     *
     * @param id 班级ID
     * @param headTeacherId 班主任ID
     */
    void setHeadTeacher(Long id, Long headTeacherId);

    /**
     * 删除班级
     *
     * @param id 班级ID
     */
    void deleteClass(Long id);

    /**
     * 分页查询班级列表
     *
     * @param request 查询请求
     * @return 分页结果
     */
    Page<ClassResponse> listClasses(ClassQueryRequest request);

}
