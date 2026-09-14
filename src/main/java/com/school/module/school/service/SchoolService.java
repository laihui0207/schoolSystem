package com.school.module.school.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.module.school.dto.SchoolCreateRequest;
import com.school.module.school.dto.SchoolQueryRequest;
import com.school.module.school.dto.SchoolResponse;

/**
 * 学校服务接口
 *
 * @author School System
 */
public interface SchoolService {

    Page<SchoolResponse> listSchools(SchoolQueryRequest req);

    SchoolResponse getById(Long id);

    SchoolResponse create(SchoolCreateRequest req);

    SchoolResponse update(Long id, SchoolCreateRequest req);

    void delete(Long id);

}
