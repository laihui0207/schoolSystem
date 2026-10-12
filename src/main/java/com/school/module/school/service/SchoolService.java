package com.school.module.school.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.module.school.dto.SchoolCreateRequest;
import com.school.module.school.dto.SchoolQueryRequest;
import com.school.module.school.dto.SchoolResponse;
import com.school.module.school.dto.SchoolUpdateRequest;

/**
 * 学校服务接口
 *
 * @author School System
 */
public interface SchoolService {

    /**
     * 创建学校
     */
    SchoolResponse createSchool(SchoolCreateRequest request);

    /**
     * 根据ID查询学校详情
     */
    SchoolResponse getSchoolById(Long id);

    /**
     * 更新学校信息
     */
    SchoolResponse updateSchool(Long id, SchoolUpdateRequest request);

    /**
     * 更新学校状态
     */
    void updateSchoolStatus(Long id, Integer status);

    /**
     * 分页查询学校列表
     */
    Page<SchoolResponse> listSchools(SchoolQueryRequest request);

}
