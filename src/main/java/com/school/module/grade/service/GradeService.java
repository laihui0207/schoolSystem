package com.school.module.grade.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.module.grade.dto.GradeCreateRequest;
import com.school.module.grade.dto.GradeQueryRequest;
import com.school.module.grade.dto.GradeResponse;
import com.school.module.grade.dto.GradeUpdateRequest;

import java.util.List;

/**
 * 年级服务接口
 *
 * @author School System
 */
public interface GradeService {

    Page<GradeResponse> queryGrades(GradeQueryRequest req);

    List<GradeResponse> listBySchoolId(Long schoolId);

    GradeResponse getById(Long id);

    GradeResponse create(GradeCreateRequest req);

    GradeResponse update(Long id, GradeUpdateRequest req);

    void delete(Long id);

}
