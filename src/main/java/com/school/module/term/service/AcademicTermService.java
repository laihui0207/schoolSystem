package com.school.module.term.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.module.term.dto.AcademicTermCreateRequest;
import com.school.module.term.dto.AcademicTermQueryRequest;
import com.school.module.term.dto.AcademicTermResponse;

/**
 * 学期服务接口
 *
 * @author School System
 */
public interface AcademicTermService {

    Page<AcademicTermResponse> queryTerms(AcademicTermQueryRequest req);

    AcademicTermResponse getById(Long id);

    /** 获取指定学校当前进行中的学期 */
    AcademicTermResponse getCurrentTerm(Long schoolId);

    AcademicTermResponse create(AcademicTermCreateRequest req);

    AcademicTermResponse update(Long id, AcademicTermCreateRequest req);

    void delete(Long id);

}
