package com.school.module.exam.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.common.exception.BusinessException;
import com.school.module.exam.dto.ExamCreateRequest;
import com.school.module.exam.dto.ExamProgressItemDto;
import com.school.module.exam.dto.ExamProgressResponse;
import com.school.module.exam.dto.ExamQueryRequest;
import com.school.module.exam.dto.ExamResponse;
import com.school.module.exam.dto.ExamUpdateRequest;
import com.school.module.exam.entity.Exam;
import com.school.module.exam.entity.ExamSubject;
import com.school.module.exam.mapper.ExamMapper;
import com.school.module.exam.mapper.ExamSubjectMapper;
import com.school.module.score.mapper.ScoreMapper;
import com.school.module.term.entity.AcademicTerm;
import com.school.module.term.mapper.AcademicTermMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 考试服务实现类
 *
 * @author School System
 */
@Service
@RequiredArgsConstructor
public class ExamServiceImpl implements ExamService {

    private final ExamMapper examMapper;
    private final ExamSubjectMapper examSubjectMapper;
    private final AcademicTermMapper termMapper;
    private final ScoreMapper scoreMapper;

    private static final List<Integer> VALID_EXAM_TYPES = Arrays.asList(1, 2, 3, 4, 5);
    private static final List<Integer> VALID_STATUS = Arrays.asList(0, 1, 2, 3);

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExamResponse createExam(ExamCreateRequest request) {
        // 校验考试类型
        if (!VALID_EXAM_TYPES.contains(request.getExamType())) {
            throw new BusinessException("考试类型无效");
        }

        // 构建考试实体
        Exam exam = new Exam();
        exam.setSchoolId(request.getSchoolId());
        exam.setTermId(request.getTermId());
        exam.setExamName(request.getExamName());
        exam.setExamType(request.getExamType());
        exam.setExamDate(request.getExamDate());
        exam.setGradeIds(request.getGradeIds().stream()
                .map(String::valueOf)
                .collect(Collectors.joining(",")));
        exam.setStatus(0); // 默认未开始

        // 保存考试
        examMapper.insert(exam);

        // 保存考试科目关联
        for (ExamCreateRequest.ExamSubjectInfo subjectInfo : request.getSubjects()) {
            ExamSubject examSubject = new ExamSubject();
            examSubject.setExamId(exam.getId());
            examSubject.setSubjectId(subjectInfo.getSubjectId());
            examSubject.setFullScore(subjectInfo.getFullScore() != null ?
                    subjectInfo.getFullScore() : BigDecimal.valueOf(100));
            examSubject.setSortOrder(subjectInfo.getSortOrder() != null ?
                    subjectInfo.getSortOrder() : 0);
            examSubject.setDeleted(0);

            examSubjectMapper.insert(examSubject);
        }

        return convertToResponse(exam);
    }

    @Override
    public ExamResponse getExamById(Long id) {
        Exam exam = examMapper.selectById(id);
        if (exam == null) {
            throw new BusinessException("考试不存在");
        }
        return convertToResponse(exam);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExamResponse updateExam(Long id, ExamUpdateRequest request) {
        // 查询考试是否存在
        Exam existingExam = examMapper.selectById(id);
        if (existingExam == null) {
            throw new BusinessException("考试不存在");
        }

        // 只允许未开始的考试修改
        if (existingExam.getStatus() != 0) {
            throw new BusinessException("只能修改未开始的考试");
        }

        // 校验考试类型
        if (request.getExamType() != null && !VALID_EXAM_TYPES.contains(request.getExamType())) {
            throw new BusinessException("考试类型无效");
        }

        // 构建更新对象
        Exam updatedExam = new Exam();
        updatedExam.setId(existingExam.getId());
        updatedExam.setSchoolId(existingExam.getSchoolId());
        updatedExam.setTermId(existingExam.getTermId());
        updatedExam.setExamName(request.getExamName() != null ?
                request.getExamName() : existingExam.getExamName());
        updatedExam.setExamType(request.getExamType() != null ?
                request.getExamType() : existingExam.getExamType());
        updatedExam.setExamDate(request.getExamDate() != null ?
                request.getExamDate() : existingExam.getExamDate());

        if (request.getGradeIds() != null && !request.getGradeIds().isEmpty()) {
            updatedExam.setGradeIds(request.getGradeIds().stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(",")));
        } else {
            updatedExam.setGradeIds(existingExam.getGradeIds());
        }

        updatedExam.setStatus(existingExam.getStatus());

        // 更新考试
        examMapper.updateById(updatedExam);

        // 更新科目关联
        if (request.getSubjects() != null && !request.getSubjects().isEmpty()) {
            // 删除旧的科目关联
            examSubjectMapper.deleteByExamId(id);

            // 添加新的科目关联
            for (ExamUpdateRequest.ExamSubjectInfo subjectInfo : request.getSubjects()) {
                ExamSubject examSubject = new ExamSubject();
                examSubject.setExamId(id);
                examSubject.setSubjectId(subjectInfo.getSubjectId());
                examSubject.setFullScore(subjectInfo.getFullScore() != null ?
                        subjectInfo.getFullScore() : BigDecimal.valueOf(100));
                examSubject.setSortOrder(subjectInfo.getSortOrder() != null ?
                        subjectInfo.getSortOrder() : 0);
                examSubject.setDeleted(0);

                examSubjectMapper.insert(examSubject);
            }
        }

        return convertToResponse(updatedExam);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateExamStatus(Long id, Integer status) {
        // 校验状态值
        if (!VALID_STATUS.contains(status)) {
            throw new BusinessException("状态值无效");
        }

        // 查询考试是否存在
        Exam existingExam = examMapper.selectById(id);
        if (existingExam == null) {
            throw new BusinessException("考试不存在");
        }

        // 构建更新对象
        Exam updatedExam = new Exam();
        updatedExam.setId(existingExam.getId());
        updatedExam.setSchoolId(existingExam.getSchoolId());
        updatedExam.setTermId(existingExam.getTermId());
        updatedExam.setExamName(existingExam.getExamName());
        updatedExam.setExamType(existingExam.getExamType());
        updatedExam.setExamDate(existingExam.getExamDate());
        updatedExam.setGradeIds(existingExam.getGradeIds());
        updatedExam.setStatus(status);

        // 更新数据库
        examMapper.updateById(updatedExam);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteExam(Long id) {
        // 查询考试是否存在
        Exam existingExam = examMapper.selectById(id);
        if (existingExam == null) {
            throw new BusinessException("考试不存在");
        }

        // 只允许删除未开始的考试
        if (existingExam.getStatus() != 0) {
            throw new BusinessException("只能删除未开始的考试");
        }

        // 删除科目关联
        examSubjectMapper.deleteByExamId(id);

        // 逻辑删除考试
        examMapper.deleteById(id);
    }

    @Override
    public Page<ExamResponse> listExams(ExamQueryRequest request) {
        // 构建分页对象
        Page<Exam> page = new Page<>(request.getPageNum(), request.getPageSize());

        // 分页查询
        Page<Exam> resultPage = examMapper.selectPageByQuery(page, request);

        // 转换为响应DTO
        Page<ExamResponse> responsePage = new Page<>(
                resultPage.getCurrent(),
                resultPage.getSize(),
                resultPage.getTotal()
        );

        List<ExamResponse> responses = resultPage.getRecords().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());

        responsePage.setRecords(responses);

        return responsePage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void startScoreInput(Long id) {
        updateExamStatus(id, 1); // 状态改为录入中
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitForReview(Long id) {
        Exam exam = examMapper.selectById(id);
        if (exam == null) {
            throw new BusinessException("考试不存在");
        }

        if (exam.getStatus() != 1) {
            throw new BusinessException("只能提交录入中的考试");
        }

        updateExamStatus(id, 2); // 状态改为审核中
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publishExam(Long id) {
        Exam exam = examMapper.selectById(id);
        if (exam == null) {
            throw new BusinessException("考试不存在");
        }

        if (exam.getStatus() != 2) {
            throw new BusinessException("只能发布审核中的考试");
        }

        updateExamStatus(id, 3); // 状态改为已发布
    }

    /**
     * 转换为响应DTO
     */
    private ExamResponse convertToResponse(Exam exam) {
        // 查询科目信息
        List<ExamSubject> examSubjects = examSubjectMapper.selectByExamId(exam.getId());
        List<ExamResponse.SubjectInfo> subjectInfos = examSubjects.stream()
                .map(es -> ExamResponse.SubjectInfo.builder()
                        .subjectId(es.getSubjectId())
                        .fullScore(es.getFullScore())
                        .sortOrder(es.getSortOrder())
                        .build())
                .collect(Collectors.toList());

        // 解析年级ID列表
        List<Long> gradeIds = exam.getGradeIds() != null && !exam.getGradeIds().isEmpty() ?
                Arrays.stream(exam.getGradeIds().split(","))
                        .map(Long::parseLong)
                        .collect(Collectors.toList()) :
                List.of();

        // 查询学期名称
        String termName = null;
        if (exam.getTermId() != null) {
            AcademicTerm term = termMapper.selectById(exam.getTermId());
            if (term != null) {
                termName = term.getYear() + " 第" + (term.getTerm() == 1 ? "一" : "二") + "学期";
            }
        }

        return ExamResponse.builder()
                .id(exam.getId())
                .schoolId(exam.getSchoolId())
                .termId(exam.getTermId())
                .termName(termName)
                .examName(exam.getExamName())
                .examType(exam.getExamType())
                .examTypeName(getExamTypeName(exam.getExamType()))
                .examDate(exam.getExamDate())
                .gradeIds(gradeIds)
                .status(exam.getStatus())
                .statusName(getStatusName(exam.getStatus()))
                .subjects(subjectInfos)
                .createdAt(exam.getCreatedAt())
                .updatedAt(exam.getUpdatedAt())
                .build();
    }

    /**
     * 获取考试录入进度
     */
    public ExamProgressResponse getExamProgress(Long examId) {
        List<Map<String, Object>> rows = scoreMapper.selectExamProgress(examId);
        List<ExamProgressItemDto> items = rows.stream().map(r -> {
            ExamProgressItemDto dto = new ExamProgressItemDto();
            dto.setGradeId(toLong(r.get("gradeId")));
            dto.setGradeName((String) r.get("gradeName"));
            dto.setClassId(toLong(r.get("classId")));
            dto.setClassName((String) r.get("className"));
            dto.setSubjectId(toLong(r.get("subjectId")));
            dto.setSubjectName((String) r.get("subjectName"));
            dto.setTotalCount(toInt(r.get("totalCount")));
            dto.setEnteredCount(toInt(r.get("enteredCount")));
            return dto;
        }).collect(Collectors.toList());
        ExamProgressResponse resp = new ExamProgressResponse();
        resp.setExamId(examId);
        resp.setItems(items);
        return resp;
    }

    private Long toLong(Object v) { return v == null ? null : ((Number) v).longValue(); }
    private int toInt(Object v) { return v == null ? 0 : ((Number) v).intValue(); }

    /**
     * 获取考试类型名称
     */
    private String getExamTypeName(Integer examType) {
        if (examType == null) {
            return null;
        }
        return switch (examType) {
            case 1 -> "月考";
            case 2 -> "期中";
            case 3 -> "期末";
            case 4 -> "模拟";
            case 5 -> "其他";
            default -> "未知";
        };
    }

    /**
     * 获取状态名称
     */
    private String getStatusName(Integer status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case 0 -> "未开始";
            case 1 -> "录入中";
            case 2 -> "审核中";
            case 3 -> "已发布";
            default -> "未知";
        };
    }

}
