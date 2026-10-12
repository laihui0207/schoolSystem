package com.school.module.term.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.module.term.dto.AcademicTermCreateRequest;
import com.school.module.term.dto.AcademicTermQueryRequest;
import com.school.module.term.dto.AcademicTermResponse;
import com.school.module.term.dto.AcademicTermUpdateRequest;

import java.util.List;

/**
 * 学年学期服务接口
 *
 * @author School System
 */
public interface AcademicTermService {

    /**
     * 创建学期
     */
    AcademicTermResponse createTerm(AcademicTermCreateRequest request);

    /**
     * 根据ID查询学期详情
     */
    AcademicTermResponse getById(Long id);

    /**
     * 更新学期
     */
    AcademicTermResponse updateTerm(Long id, AcademicTermUpdateRequest request);

    /**
     * 结束学期（设置status=0）
     */
    void endTerm(Long id);

    /**
     * 分页查询学期列表
     */
    Page<AcademicTermResponse> queryTerms(AcademicTermQueryRequest request);

    /**
     * 获取当前学期
     */
    AcademicTermResponse getCurrentTerm(Long schoolId);

    /**
     * 按学校查询所有学期
     */
    List<AcademicTermResponse> listBySchool(Long schoolId);

    /**
     * 删除学期（逻辑删除）
     */
    void deleteTerm(Long id);

}
