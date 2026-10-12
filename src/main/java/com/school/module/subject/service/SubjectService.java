package com.school.module.subject.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.module.subject.dto.SubjectCreateRequest;
import com.school.module.subject.dto.SubjectQueryRequest;
import com.school.module.subject.dto.SubjectResponse;
import com.school.module.subject.dto.SubjectUpdateRequest;

import java.util.List;

/**
 * 科目服务接口
 *
 * @author School System
 */
public interface SubjectService {

    Page<SubjectResponse> querySubjects(SubjectQueryRequest req);

    List<SubjectResponse> listBySchoolId(Long schoolId);

    SubjectResponse getById(Long id);

    SubjectResponse create(SubjectCreateRequest req);

    SubjectResponse update(Long id, SubjectUpdateRequest req);

    void delete(Long id);

}
