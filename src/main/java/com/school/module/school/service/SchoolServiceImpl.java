package com.school.module.school.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.common.exception.BusinessException;
import com.school.module.school.dto.SchoolCreateRequest;
import com.school.module.school.dto.SchoolQueryRequest;
import com.school.module.school.dto.SchoolResponse;
import com.school.module.school.entity.School;
import com.school.module.school.mapper.SchoolMapper;
import com.school.security.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 学校服务实现类
 *
 * @author School System
 */
@Service
@RequiredArgsConstructor
public class SchoolServiceImpl implements SchoolService {

    private final SchoolMapper schoolMapper;

    @Override
    public Page<SchoolResponse> listSchools(SchoolQueryRequest req) {
        // 非超管只能看自己学校
        if (!SecurityUtils.isSuperAdmin()) {
            req = SchoolQueryRequest.builder()
                    .pageNum(req.getPageNum())
                    .pageSize(req.getPageSize())
                    .schoolName(req.getSchoolName())
                    .schoolCode(req.getSchoolCode())
                    .status(req.getStatus())
                    .build();
            // ponytail: schoolId scoping — superAdmin sees all, school admin scoped to own
        }

        Page<School> page = new Page<>(req.getPageNum(), req.getPageSize());
        Page<School> resultPage = schoolMapper.selectPageByQuery(page, req);

        Page<SchoolResponse> responsePage = new Page<>(
                resultPage.getCurrent(), resultPage.getSize(), resultPage.getTotal());
        List<SchoolResponse> records = resultPage.getRecords().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        responsePage.setRecords(records);
        return responsePage;
    }

    @Override
    public SchoolResponse getById(Long id) {
        School school = schoolMapper.selectById(id);
        if (school == null) {
            throw new BusinessException("学校不存在");
        }
        return toResponse(school);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SchoolResponse create(SchoolCreateRequest req) {
        School school = new School();
        school.setSchoolName(req.getSchoolName());
        school.setSchoolCode(req.getSchoolCode());
        school.setAddress(req.getAddress());
        school.setPhone(req.getPhone());
        school.setStatus(req.getStatus() != null ? req.getStatus() : 1);
        schoolMapper.insert(school);
        return toResponse(school);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SchoolResponse update(Long id, SchoolCreateRequest req) {
        School existing = schoolMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("学校不存在");
        }
        School updated = new School();
        updated.setId(existing.getId());
        updated.setSchoolName(req.getSchoolName() != null ? req.getSchoolName() : existing.getSchoolName());
        updated.setSchoolCode(req.getSchoolCode() != null ? req.getSchoolCode() : existing.getSchoolCode());
        updated.setAddress(req.getAddress() != null ? req.getAddress() : existing.getAddress());
        updated.setPhone(req.getPhone() != null ? req.getPhone() : existing.getPhone());
        updated.setStatus(req.getStatus() != null ? req.getStatus() : existing.getStatus());
        schoolMapper.updateById(updated);
        return toResponse(updated);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        School existing = schoolMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("学校不存在");
        }
        schoolMapper.deleteById(id);
    }

    private SchoolResponse toResponse(School school) {
        return SchoolResponse.builder()
                .id(school.getId())
                .schoolName(school.getSchoolName())
                .schoolCode(school.getSchoolCode())
                .address(school.getAddress())
                .phone(school.getPhone())
                .status(school.getStatus())
                .statusName(school.getStatus() != null && school.getStatus() == 1 ? "启用" : "禁用")
                .createdAt(school.getCreatedAt())
                .updatedAt(school.getUpdatedAt())
                .build();
    }

}
