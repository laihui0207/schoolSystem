package com.school.module.term.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.common.exception.BusinessException;
import com.school.module.class_module.entity.SchoolClass;
import com.school.module.class_module.mapper.ClassMapper;
import com.school.module.exam.entity.Exam;
import com.school.module.exam.mapper.ExamMapper;
import com.school.module.term.dto.AcademicTermCreateRequest;
import com.school.module.term.dto.AcademicTermQueryRequest;
import com.school.module.term.dto.AcademicTermResponse;
import com.school.module.term.dto.AcademicTermUpdateRequest;
import com.school.module.term.entity.AcademicTerm;
import com.school.module.term.mapper.AcademicTermMapper;
import com.school.security.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 学年学期服务实现类
 *
 * @author School System
 */
@Service
@RequiredArgsConstructor
public class AcademicTermServiceImpl implements AcademicTermService {

    private final AcademicTermMapper termMapper;
    // ponytail: 只读注入做引用计数，不往 class/exam 模块的 Mapper/Service 里加方法
    private final ClassMapper classMapper;
    private final ExamMapper examMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AcademicTermResponse createTerm(AcademicTermCreateRequest request) {
        // 参数校验
        if (request == null) {
            throw new IllegalArgumentException("创建请求不能为空");
        }

        // 校验学期值
        if (request.getTerm() == null || (request.getTerm() != 1 && request.getTerm() != 2)) {
            throw new IllegalArgumentException("学期只能是1或2");
        }

        // 校验日期范围
        if (request.getStartDate() != null && request.getEndDate() != null) {
            if (!request.getStartDate().isBefore(request.getEndDate())) {
                throw new IllegalArgumentException("开始日期必须早于结束日期");
            }
        }

        // 归属学校取自当前身份，两处校验与写入必须用同一个值
        Long schoolId = SecurityUtils.resolveSchoolId(request.getSchoolId());

        // 检查该学校的学年学期是否已存在
        AcademicTerm existingTerm = termMapper.selectBySchoolYearTerm(
                schoolId, request.getYear(), request.getTerm());
        if (existingTerm != null) {
            throw new BusinessException("该学校的学年学期已存在");
        }

        // 检查是否已有进行中的学期
        AcademicTerm activeTerm = termMapper.selectActiveTermBySchool(schoolId);
        if (activeTerm != null) {
            throw new BusinessException("该学校已有进行中的学期，请先结束当前学期");
        }

        // 构建学期实体
        AcademicTerm term = buildTermFromCreateRequest(request, schoolId);
        term.setStatus(1); // 默认进行中

        // 插入数据库
        termMapper.insert(term);

        // 返回响应
        return convertToResponse(term);
    }

    @Override
    public AcademicTermResponse getById(Long id) {
        AcademicTerm term = termMapper.selectById(id);
        if (term == null) {
            throw new BusinessException("学年学期不存在");
        }
        return convertToResponse(term);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AcademicTermResponse updateTerm(Long id, AcademicTermUpdateRequest request) {
        // 查询学期是否存在
        AcademicTerm existingTerm = termMapper.selectById(id);
        if (existingTerm == null) {
            throw new BusinessException("学年学期不存在");
        }

        // 校验日期范围
        LocalDate newStartDate = request.getStartDate() != null ? request.getStartDate() : existingTerm.getStartDate();
        LocalDate newEndDate = request.getEndDate() != null ? request.getEndDate() : existingTerm.getEndDate();

        // ponytail: G-5 — 历史数据 start_date/end_date 可空，任一为 null 时跳过比较而不是 NPE 变 500
        if (newStartDate != null && newEndDate != null && !newStartDate.isBefore(newEndDate)) {
            throw new IllegalArgumentException("开始日期必须早于结束日期");
        }

        // 校验学期值
        if (request.getTerm() != null && request.getTerm() != 1 && request.getTerm() != 2) {
            throw new IllegalArgumentException("学期只能是1或2");
        }

        // ponytail: G-7 — 改 year/term 时复用 createTerm 的 (school, year, term) 唯一校验，库里没有唯一索引兜底
        String newYear = request.getYear() != null ? request.getYear() : existingTerm.getYear();
        Integer newTerm = request.getTerm() != null ? request.getTerm() : existingTerm.getTerm();
        boolean keyChanged = !Objects.equals(newYear, existingTerm.getYear())
                || !Objects.equals(newTerm, existingTerm.getTerm());
        if (keyChanged) {
            AcademicTerm sameKey = termMapper.selectBySchoolYearTerm(existingTerm.getSchoolId(), newYear, newTerm);
            if (sameKey != null && !sameKey.getId().equals(id)) {
                throw new BusinessException("该学校的学年学期已存在");
            }
        }

        // 构建更新后的学期对象（不可变方式）
        AcademicTerm updatedTerm = buildTermFromUpdateRequest(existingTerm, request);

        // 更新数据库
        termMapper.updateById(updatedTerm);

        // 返回更新后的数据
        return convertToResponse(updatedTerm);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void endTerm(Long id) {
        // 查询学期是否存在
        AcademicTerm existingTerm = termMapper.selectById(id);
        if (existingTerm == null) {
            throw new BusinessException("学年学期不存在");
        }

        // 构建更新对象（不可变方式）
        AcademicTerm updatedTerm = new AcademicTerm();
        updatedTerm.setId(existingTerm.getId());
        updatedTerm.setSchoolId(existingTerm.getSchoolId());
        updatedTerm.setYear(existingTerm.getYear());
        updatedTerm.setTerm(existingTerm.getTerm());
        updatedTerm.setStartDate(existingTerm.getStartDate());
        updatedTerm.setEndDate(existingTerm.getEndDate());
        updatedTerm.setStatus(0); // 设置为已结束

        // 更新数据库
        termMapper.updateById(updatedTerm);
    }

    @Override
    public Page<AcademicTermResponse> queryTerms(AcademicTermQueryRequest request) {
        // 构建分页对象
        Page<AcademicTerm> page = new Page<>(request.getPageNum(), request.getPageSize());

        // 分页查询
        Page<AcademicTerm> resultPage = termMapper.selectPageByQuery(page, request);

        // 转换为响应DTO
        Page<AcademicTermResponse> responsePage = new Page<>(
                resultPage.getCurrent(),
                resultPage.getSize(),
                resultPage.getTotal()
        );

        List<AcademicTermResponse> responses = resultPage.getRecords().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());

        responsePage.setRecords(responses);

        return responsePage;
    }

    @Override
    public AcademicTermResponse getCurrentTerm(Long schoolId) {
        AcademicTerm term = termMapper.selectCurrentTerm(schoolId);
        if (term == null) {
            throw new BusinessException("当前没有进行中的学期");
        }
        return convertToResponse(term);
    }

    @Override
    public List<AcademicTermResponse> listBySchool(Long schoolId) {
        List<AcademicTerm> terms = termMapper.selectBySchoolId(schoolId);
        return terms.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTerm(Long id) {
        AcademicTerm existing = termMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("学年学期不存在");
        }
        // ponytail: 前端删除确认文案已承诺「被班级/考试引用时后端会拒绝」；没有外键约束，所以这里显式计数拦截
        Long classRefs = classMapper.selectCount(new LambdaQueryWrapper<SchoolClass>()
                .eq(SchoolClass::getTermId, id));
        if (classRefs != null && classRefs > 0) {
            throw new BusinessException("该学期仍被 " + classRefs + " 个班级引用，无法删除");
        }
        Long examRefs = examMapper.selectCount(new LambdaQueryWrapper<Exam>()
                .eq(Exam::getTermId, id));
        if (examRefs != null && examRefs > 0) {
            throw new BusinessException("该学期仍被 " + examRefs + " 场考试引用，无法删除");
        }
        termMapper.deleteById(id);
    }

    /**
     * 从创建请求构建学期实体
     */
    private AcademicTerm buildTermFromCreateRequest(AcademicTermCreateRequest request, Long schoolId) {
        AcademicTerm term = new AcademicTerm();
        term.setSchoolId(schoolId);
        term.setYear(request.getYear());
        term.setTerm(request.getTerm());
        term.setStartDate(request.getStartDate());
        term.setEndDate(request.getEndDate());
        return term;
    }

    /**
     * 从更新请求构建学期实体（不可变方式）
     */
    private AcademicTerm buildTermFromUpdateRequest(AcademicTerm existing, AcademicTermUpdateRequest request) {
        AcademicTerm term = new AcademicTerm();
        term.setId(existing.getId());
        term.setSchoolId(existing.getSchoolId()); // 学校ID不允许修改
        term.setYear(request.getYear() != null ? request.getYear() : existing.getYear());
        term.setTerm(request.getTerm() != null ? request.getTerm() : existing.getTerm());
        term.setStartDate(request.getStartDate() != null ? request.getStartDate() : existing.getStartDate());
        term.setEndDate(request.getEndDate() != null ? request.getEndDate() : existing.getEndDate());
        term.setStatus(existing.getStatus()); // 状态通过endTerm方法修改
        return term;
    }

    /**
     * 转换为响应DTO
     */
    private AcademicTermResponse convertToResponse(AcademicTerm term) {
        return AcademicTermResponse.builder()
                .id(term.getId())
                .schoolId(term.getSchoolId())
                .year(term.getYear())
                .term(term.getTerm())
                .startDate(term.getStartDate())
                .endDate(term.getEndDate())
                .status(term.getStatus())
                .createdAt(term.getCreatedAt())
                .updatedAt(term.getUpdatedAt())
                .build();
    }

}
