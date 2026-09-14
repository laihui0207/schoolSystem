package com.school.module.term.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.common.exception.BusinessException;
import com.school.module.term.dto.AcademicTermCreateRequest;
import com.school.module.term.dto.AcademicTermQueryRequest;
import com.school.module.term.dto.AcademicTermResponse;
import com.school.module.term.entity.AcademicTerm;
import com.school.module.term.mapper.AcademicTermMapper;
import com.school.security.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 学期服务实现类
 *
 * @author School System
 */
@Service
@RequiredArgsConstructor
public class AcademicTermServiceImpl implements AcademicTermService {

    private final AcademicTermMapper academicTermMapper;

    @Override
    public Page<AcademicTermResponse> queryTerms(AcademicTermQueryRequest req) {
        if (!SecurityUtils.isSuperAdmin() && req.getSchoolId() == null) {
            req = AcademicTermQueryRequest.builder()
                    .pageNum(req.getPageNum())
                    .pageSize(req.getPageSize())
                    .year(req.getYear())
                    .term(req.getTerm())
                    .status(req.getStatus())
                    .schoolId(SecurityUtils.currentSchoolId())
                    .build();
        }
        Page<AcademicTerm> page = new Page<>(req.getPageNum(), req.getPageSize());
        Page<AcademicTerm> resultPage = academicTermMapper.selectPageByQuery(page, req);

        Page<AcademicTermResponse> responsePage = new Page<>(
                resultPage.getCurrent(), resultPage.getSize(), resultPage.getTotal());
        List<AcademicTermResponse> records = resultPage.getRecords().stream()
                .map(this::toResponse).collect(Collectors.toList());
        responsePage.setRecords(records);
        return responsePage;
    }

    @Override
    public AcademicTermResponse getCurrentTerm(Long schoolId) {
        // 查找该学校状态为进行中(1)的学期
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AcademicTerm> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AcademicTerm>()
                        .eq(AcademicTerm::getSchoolId, schoolId)
                        .eq(AcademicTerm::getStatus, 1)
                        .last("LIMIT 1");
        AcademicTerm term = academicTermMapper.selectOne(wrapper);
        if (term == null) {
            throw new BusinessException("当前学校无进行中的学期");
        }
        return toResponse(term);
    }

    @Override
    public AcademicTermResponse getById(Long id) {
        AcademicTerm term = academicTermMapper.selectById(id);
        if (term == null) {
            throw new BusinessException("学期不存在");
        }
        return toResponse(term);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AcademicTermResponse create(AcademicTermCreateRequest req) {
        AcademicTerm term = new AcademicTerm();
        term.setSchoolId(req.getSchoolId());
        term.setYear(req.getYear());
        term.setTerm(req.getTerm());
        term.setStartDate(req.getStartDate());
        term.setEndDate(req.getEndDate());
        term.setStatus(req.getStatus() != null ? req.getStatus() : 1);
        academicTermMapper.insert(term);
        return toResponse(term);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AcademicTermResponse update(Long id, AcademicTermCreateRequest req) {
        AcademicTerm existing = academicTermMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("学期不存在");
        }
        AcademicTerm updated = new AcademicTerm();
        updated.setId(existing.getId());
        updated.setSchoolId(existing.getSchoolId());
        updated.setYear(req.getYear() != null ? req.getYear() : existing.getYear());
        updated.setTerm(req.getTerm() != null ? req.getTerm() : existing.getTerm());
        updated.setStartDate(req.getStartDate() != null ? req.getStartDate() : existing.getStartDate());
        updated.setEndDate(req.getEndDate() != null ? req.getEndDate() : existing.getEndDate());
        updated.setStatus(req.getStatus() != null ? req.getStatus() : existing.getStatus());
        academicTermMapper.updateById(updated);
        return toResponse(updated);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        AcademicTerm existing = academicTermMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("学期不存在");
        }
        academicTermMapper.deleteById(id);
    }

    private AcademicTermResponse toResponse(AcademicTerm term) {
        return AcademicTermResponse.builder()
                .id(term.getId())
                .schoolId(term.getSchoolId())
                .year(term.getYear())
                .term(term.getTerm())
                .termName(getTermName(term.getTerm()))
                .startDate(term.getStartDate())
                .endDate(term.getEndDate())
                .status(term.getStatus())
                .statusName(term.getStatus() != null && term.getStatus() == 1 ? "进行中" : "已结束")
                .createdAt(term.getCreatedAt())
                .updatedAt(term.getUpdatedAt())
                .build();
    }

    private String getTermName(Integer term) {
        if (term == null) return null;
        return switch (term) {
            case 1 -> "上学期";
            case 2 -> "下学期";
            default -> "未知";
        };
    }

}
