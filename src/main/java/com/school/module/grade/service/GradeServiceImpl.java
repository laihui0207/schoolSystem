package com.school.module.grade.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.common.exception.BusinessException;
import com.school.module.grade.dto.GradeCreateRequest;
import com.school.module.grade.dto.GradeQueryRequest;
import com.school.module.grade.dto.GradeResponse;
import com.school.module.grade.entity.Grade;
import com.school.module.grade.mapper.GradeMapper;
import com.school.module.school.entity.School;
import com.school.module.school.mapper.SchoolMapper;
import com.school.security.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 年级服务实现类
 *
 * @author School System
 */
@Service
@RequiredArgsConstructor
public class GradeServiceImpl implements GradeService {

    private final GradeMapper gradeMapper;
    private final SchoolMapper schoolMapper;

    @Override
    public Page<GradeResponse> queryGrades(GradeQueryRequest req) {
        // 非超管自动限定本校
        if (!SecurityUtils.isSuperAdmin() && req.getSchoolId() == null) {
            req = GradeQueryRequest.builder()
                    .pageNum(req.getPageNum())
                    .pageSize(req.getPageSize())
                    .gradeName(req.getGradeName())
                    .gradeCode(req.getGradeCode())
                    .schoolId(SecurityUtils.currentSchoolId())
                    .build();
        }
        Page<Grade> page = new Page<>(req.getPageNum(), req.getPageSize());
        Page<Grade> resultPage = gradeMapper.selectPageByQuery(page, req);

        Page<GradeResponse> responsePage = new Page<>(
                resultPage.getCurrent(), resultPage.getSize(), resultPage.getTotal());
        responsePage.setRecords(resultPage.getRecords().stream()
                .map(this::toResponse).collect(Collectors.toList()));
        return responsePage;
    }

    @Override
    public List<GradeResponse> listBySchoolId(Long schoolId) {
        return gradeMapper.selectBySchoolId(schoolId).stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public GradeResponse getById(Long id) {
        Grade grade = gradeMapper.selectById(id);
        if (grade == null) {
            throw new BusinessException("年级不存在");
        }
        return toResponse(grade);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GradeResponse create(GradeCreateRequest req) {
        School school = schoolMapper.selectById(req.getSchoolId());
        if (school == null) {
            throw new BusinessException("学校不存在");
        }
        Grade existing = gradeMapper.selectBySchoolIdAndCode(req.getSchoolId(), req.getGradeCode());
        if (existing != null) {
            throw new BusinessException("该年级编码在本校已存在");
        }
        Grade grade = new Grade();
        grade.setSchoolId(req.getSchoolId());
        grade.setGradeName(req.getGradeName());
        grade.setGradeCode(req.getGradeCode());
        gradeMapper.insert(grade);
        return toResponse(grade);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GradeResponse update(Long id, GradeCreateRequest req) {
        Grade existing = gradeMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("年级不存在");
        }
        Grade updated = new Grade();
        updated.setId(existing.getId());
        updated.setSchoolId(existing.getSchoolId());
        updated.setGradeName(req.getGradeName() != null ? req.getGradeName() : existing.getGradeName());
        updated.setGradeCode(req.getGradeCode() != null ? req.getGradeCode() : existing.getGradeCode());
        gradeMapper.updateById(updated);
        return toResponse(updated);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Grade existing = gradeMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("年级不存在");
        }
        gradeMapper.deleteById(id);
    }

    private GradeResponse toResponse(Grade grade) {
        return GradeResponse.builder()
                .id(grade.getId())
                .schoolId(grade.getSchoolId())
                .gradeName(grade.getGradeName())
                .gradeCode(grade.getGradeCode())
                .createdAt(grade.getCreatedAt())
                .updatedAt(grade.getUpdatedAt())
                .build();
    }

}
