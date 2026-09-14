package com.school.module.subject.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.common.exception.BusinessException;
import com.school.module.subject.dto.SubjectCreateRequest;
import com.school.module.subject.dto.SubjectQueryRequest;
import com.school.module.subject.dto.SubjectResponse;
import com.school.module.subject.entity.Subject;
import com.school.module.subject.mapper.SubjectMapper;
import com.school.security.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 科目服务实现类
 *
 * @author School System
 */
@Service
@RequiredArgsConstructor
public class SubjectServiceImpl implements SubjectService {

    private final SubjectMapper subjectMapper;

    @Override
    public Page<SubjectResponse> querySubjects(SubjectQueryRequest req) {
        if (!SecurityUtils.isSuperAdmin() && req.getSchoolId() == null) {
            req = SubjectQueryRequest.builder()
                    .pageNum(req.getPageNum())
                    .pageSize(req.getPageSize())
                    .subjectName(req.getSubjectName())
                    .subjectCode(req.getSubjectCode())
                    .schoolId(SecurityUtils.currentSchoolId())
                    .build();
        }
        Page<Subject> page = new Page<>(req.getPageNum(), req.getPageSize());
        Page<Subject> resultPage = subjectMapper.selectPageByQuery(page, req);

        Page<SubjectResponse> responsePage = new Page<>(
                resultPage.getCurrent(), resultPage.getSize(), resultPage.getTotal());
        responsePage.setRecords(resultPage.getRecords().stream()
                .map(this::toResponse).collect(Collectors.toList()));
        return responsePage;
    }

    @Override
    public List<SubjectResponse> listBySchoolId(Long schoolId) {
        return subjectMapper.selectBySchoolId(schoolId).stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public SubjectResponse getById(Long id) {
        Subject subject = subjectMapper.selectById(id);
        if (subject == null) {
            throw new BusinessException("科目不存在");
        }
        return toResponse(subject);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SubjectResponse create(SubjectCreateRequest req) {
        Subject subject = new Subject();
        subject.setSchoolId(req.getSchoolId());
        subject.setSubjectName(req.getSubjectName());
        subject.setSubjectCode(req.getSubjectCode());
        subject.setFullScore(req.getFullScore() != null ? req.getFullScore() : new BigDecimal("100.0"));
        subject.setPassScore(req.getPassScore() != null ? req.getPassScore() : new BigDecimal("60.0"));
        subjectMapper.insert(subject);
        return toResponse(subject);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SubjectResponse update(Long id, SubjectCreateRequest req) {
        Subject existing = subjectMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("科目不存在");
        }
        Subject updated = new Subject();
        updated.setId(existing.getId());
        updated.setSchoolId(existing.getSchoolId());
        updated.setSubjectName(req.getSubjectName() != null ? req.getSubjectName() : existing.getSubjectName());
        updated.setSubjectCode(req.getSubjectCode() != null ? req.getSubjectCode() : existing.getSubjectCode());
        updated.setFullScore(req.getFullScore() != null ? req.getFullScore() : existing.getFullScore());
        updated.setPassScore(req.getPassScore() != null ? req.getPassScore() : existing.getPassScore());
        subjectMapper.updateById(updated);
        return toResponse(updated);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Subject existing = subjectMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("科目不存在");
        }
        subjectMapper.deleteById(id);
    }

    private SubjectResponse toResponse(Subject subject) {
        return SubjectResponse.builder()
                .id(subject.getId())
                .schoolId(subject.getSchoolId())
                .subjectName(subject.getSubjectName())
                .subjectCode(subject.getSubjectCode())
                .fullScore(subject.getFullScore())
                .passScore(subject.getPassScore())
                .createdAt(subject.getCreatedAt())
                .updatedAt(subject.getUpdatedAt())
                .build();
    }

}
