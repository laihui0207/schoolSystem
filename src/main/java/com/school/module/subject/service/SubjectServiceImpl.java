package com.school.module.subject.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.common.exception.BusinessException;
import com.school.module.exam.entity.ExamSubject;
import com.school.module.exam.mapper.ExamSubjectMapper;
import com.school.module.subject.dto.SubjectCreateRequest;
import com.school.module.subject.dto.SubjectQueryRequest;
import com.school.module.subject.dto.SubjectResponse;
import com.school.module.subject.dto.SubjectUpdateRequest;
import com.school.module.subject.entity.Subject;
import com.school.module.subject.mapper.SubjectMapper;
import com.school.module.teacher.entity.Teacher;
import com.school.module.teacher.mapper.TeacherMapper;
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
    // ponytail: 只读注入别的模块的 Mapper 做引用计数，不往对方 Mapper/Service 里加方法
    private final ExamSubjectMapper examSubjectMapper;
    private final TeacherMapper teacherMapper;

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
        BigDecimal fullScore = req.getFullScore() != null ? req.getFullScore() : new BigDecimal("100.0");
        BigDecimal passScore = req.getPassScore() != null ? req.getPassScore() : new BigDecimal("60.0");
        // ponytail: 库里没有 (school_id, subject_code) 唯一索引，也没有 CHECK，所以在服务层拦
        assertPassScoreNotAboveFullScore(passScore, fullScore);
        // ponytail: G-3 — 归属学校取自当前身份，否则校管理员可往别校写科目，且唯一校验被租户改写后形同虚设
        Long schoolId = SecurityUtils.resolveSchoolId(req.getSchoolId());
        assertSubjectCodeUnique(schoolId, req.getSubjectCode());
        Subject subject = new Subject();
        subject.setSchoolId(schoolId);
        subject.setSubjectName(req.getSubjectName());
        subject.setSubjectCode(req.getSubjectCode());
        subject.setFullScore(fullScore);
        subject.setPassScore(passScore);
        subjectMapper.insert(subject);
        return toResponse(subject);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SubjectResponse update(Long id, SubjectUpdateRequest req) {
        Subject existing = subjectMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("科目不存在");
        }
        BigDecimal fullScore = req.getFullScore() != null ? req.getFullScore() : existing.getFullScore();
        BigDecimal passScore = req.getPassScore() != null ? req.getPassScore() : existing.getPassScore();
        assertPassScoreNotAboveFullScore(passScore, fullScore);
        Subject updated = new Subject();
        updated.setId(existing.getId());
        // ponytail: UpdateRequest 不带 schoolId/subjectCode，归属学校与编码不可改，故更新时无需再做编码唯一校验
        updated.setSchoolId(existing.getSchoolId());
        updated.setSubjectCode(existing.getSubjectCode());
        updated.setSubjectName(req.getSubjectName() != null ? req.getSubjectName() : existing.getSubjectName());
        updated.setFullScore(fullScore);
        updated.setPassScore(passScore);
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
        // ponytail: 没有外键约束，被考试科目或教师主教科目引用的科目删掉后成绩/教师页会静默变空，这里显式拒绝；
        // ExamSubject 不继承 BaseEntity、deleted 没有 @TableLogic，必须手动加 deleted = 0
        Long examRefs = examSubjectMapper.selectCount(new LambdaQueryWrapper<ExamSubject>()
                .eq(ExamSubject::getSubjectId, id)
                .eq(ExamSubject::getDeleted, 0));
        if (examRefs != null && examRefs > 0) {
            throw new BusinessException("该科目已被 " + examRefs + " 场考试引用，无法删除");
        }
        Long teacherRefs = teacherMapper.selectCount(new LambdaQueryWrapper<Teacher>()
                .eq(Teacher::getSubjectId, id));
        if (teacherRefs != null && teacherRefs > 0) {
            throw new BusinessException("该科目仍是 " + teacherRefs + " 位教师的主教科目，无法删除");
        }
        subjectMapper.deleteById(id);
    }

    private void assertPassScoreNotAboveFullScore(BigDecimal passScore, BigDecimal fullScore) {
        if (passScore != null && fullScore != null && passScore.compareTo(fullScore) > 0) {
            throw new BusinessException("及格分不能大于满分");
        }
    }

    private void assertSubjectCodeUnique(Long schoolId, String subjectCode) {
        Long count = subjectMapper.selectCount(new LambdaQueryWrapper<Subject>()
                .eq(Subject::getSchoolId, schoolId)
                .eq(Subject::getSubjectCode, subjectCode));
        if (count != null && count > 0) {
            throw new BusinessException("该科目编码在本校已存在");
        }
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
