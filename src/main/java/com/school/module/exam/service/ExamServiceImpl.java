package com.school.module.exam.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.common.exception.BusinessException;
import com.school.module.dict.entity.DictData;
import com.school.module.dict.service.DictService;
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
import com.school.module.grade.entity.Grade;
import com.school.module.grade.mapper.GradeMapper;
import com.school.module.notification.service.NotificationService;
import com.school.module.school.entity.School;
import com.school.module.school.mapper.SchoolMapper;
import com.school.module.score.entity.Score;
import com.school.module.score.mapper.ScoreMapper;
import com.school.module.score.service.ScoreService;
import com.school.module.student.entity.Student;
import com.school.module.student.mapper.StudentMapper;
import com.school.module.subject.entity.Subject;
import com.school.module.subject.mapper.SubjectMapper;
import com.school.module.term.entity.AcademicTerm;
import com.school.module.term.mapper.AcademicTermMapper;
import com.school.module.warning.service.ScoreWarningService;
import com.school.security.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 考试服务实现类
 *
 * @author School System
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExamServiceImpl implements ExamService {

    private final ExamMapper examMapper;
    private final ExamSubjectMapper examSubjectMapper;
    private final SubjectMapper subjectMapper;
    private final AcademicTermMapper termMapper;
    private final ScoreMapper scoreMapper;
    private final GradeMapper gradeMapper;
    private final SchoolMapper schoolMapper;
    private final StudentMapper studentMapper;
    private final ScoreService scoreService;
    private final NotificationService notificationService;
    private final ScoreWarningService scoreWarningService;
    private final DictService dictService;

    /** 考试类型字典编码（sys_dict_type.dict_type，V7 种子） */
    private static final String DICT_EXAM_TYPE = "exam_type";
    /** exam_type 字典快照有效期（毫秒） */
    private static final long EXAM_TYPE_DICT_TTL_MILLIS = 60_000L;
    /**
     * exam_type 字典快照：dict_value → dict_label，整体替换而不是逐项修改。
     * ponytail: convertToResponse 按行调用 getExamTypeName，列表页若每行查一次字典就是 N+1；
     * 这里和 ScoreRuleServiceImpl 一样用时间戳做 60 秒本地缓存。
     */
    private volatile Map<String, String> examTypeLabels = Collections.emptyMap();
    private volatile long examTypeLabelsLoadedAt = 0L;

    private static final List<Integer> VALID_EXAM_TYPES = Arrays.asList(1, 2, 3, 4, 5);
    private static final List<Integer> VALID_STATUS = Arrays.asList(0, 1, 2, 3);

    private static final int STATUS_NOT_STARTED = 0;
    private static final int STATUS_INPUTTING = 1;
    private static final int STATUS_REVIEWING = 2;
    private static final int STATUS_PUBLISHED = 3;

    private static final int SCORE_STATUS_PENDING = 0;
    private static final int SCORE_STATUS_APPROVED = 1;
    private static final int SCORE_STATUS_REJECTED = 2;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExamResponse createExam(ExamCreateRequest request) {
        // 校验考试类型
        if (!VALID_EXAM_TYPES.contains(request.getExamType())) {
            throw new BusinessException("考试类型无效");
        }

        // 构建考试实体
        Exam exam = new Exam();
        exam.setSchoolId(SecurityUtils.resolveSchoolId(request.getSchoolId()));
        exam.setTermId(request.getTermId());
        exam.setExamName(request.getExamName());
        exam.setExamType(request.getExamType());
        exam.setExamDate(request.getExamDate());
        exam.setGradeIds(request.getGradeIds().stream()
                .map(String::valueOf)
                .collect(Collectors.joining(",")));
        exam.setStatus(STATUS_NOT_STARTED); // 默认未开始

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
        if (existingExam.getStatus() != STATUS_NOT_STARTED) {
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
            syncExamSubjects(id, request.getSubjects());
        }

        return convertToResponse(updatedExam);
    }

    /**
     * 按差集同步考试科目：保留的只更新满分/排序，移除的软删，新增的插入
     * <p>
     * ponytail: 不再"全部软删再重插"。V14 之前 uk_exam_subject 不含 deleted，软删行仍占着唯一键，
     * 重插同科目必撞；按差集只动真正变化的行，即使索引没迁移也不会撞键，且保留原行 id。
     */
    private void syncExamSubjects(Long examId, List<ExamUpdateRequest.ExamSubjectInfo> requested) {
        Map<Long, ExamSubject> existingBySubject = examSubjectMapper.selectByExamId(examId).stream()
                .collect(Collectors.toMap(ExamSubject::getSubjectId, Function.identity(), (a, b) -> a));

        Set<Long> keptSubjectIds = new HashSet<>();
        for (ExamUpdateRequest.ExamSubjectInfo info : requested) {
            if (info.getSubjectId() == null) {
                throw new BusinessException("科目ID不能为空");
            }
            if (!keptSubjectIds.add(info.getSubjectId())) {
                throw new BusinessException("考试科目重复：" + info.getSubjectId());
            }
            BigDecimal fullScore = info.getFullScore() != null ? info.getFullScore() : BigDecimal.valueOf(100);
            int sortOrder = info.getSortOrder() != null ? info.getSortOrder() : 0;

            ExamSubject current = existingBySubject.get(info.getSubjectId());
            if (current == null) {
                ExamSubject created = new ExamSubject();
                created.setExamId(examId);
                created.setSubjectId(info.getSubjectId());
                created.setFullScore(fullScore);
                created.setSortOrder(sortOrder);
                created.setDeleted(0);
                examSubjectMapper.insert(created);
            } else {
                ExamSubject changed = new ExamSubject();
                changed.setId(current.getId());
                changed.setFullScore(fullScore);
                changed.setSortOrder(sortOrder);
                // ExamSubject 未继承 BaseEntity，没有自动填充，手动刷新更新时间
                changed.setUpdatedAt(LocalDateTime.now());
                examSubjectMapper.updateById(changed);
            }
        }

        // ponytail: 全局 logic-delete-field=deleted 对 ExamSubject 同样生效，updateById 会忽略 deleted 列，
        // 软删必须走 deleteById（MP 生成 UPDATE ... SET deleted=1）。
        existingBySubject.values().stream()
                .filter(es -> !keptSubjectIds.contains(es.getSubjectId()))
                .forEach(es -> examSubjectMapper.deleteById(es.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateExamStatus(Long id, Integer status) {
        // 校验状态值
        if (status == null || !VALID_STATUS.contains(status)) {
            throw new BusinessException("状态值无效");
        }

        // 查询考试是否存在
        Exam existingExam = examMapper.selectById(id);
        if (existingExam == null) {
            throw new BusinessException("考试不存在");
        }

        assertTransitionAllowed(existingExam.getStatus(), status);

        writeStatus(existingExam, status);
    }

    /**
     * 落库状态变更（不做流转校验，调用方负责）
     * <p>
     * ponytail: 从 updateExamStatus 拆出，供 publishExam 在自己的待审核校验通过后直接写 2→3；
     * 公开的 updateExamStatus 对 target=3 一律拒绝，发布只能走 publishExam 闭环。
     */
    private void writeStatus(Exam existingExam, int status) {
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

    /**
     * 状态流转校验
     * <p>
     * ponytail: 原实现允许任意跳转（如 3→0），已发布的考试被改回未开始后可被编辑/删除，成绩与排名却还在。
     * 现只允许前进一步；管理员可回退一步（3→2 撤回发布、2→1 退回录入）。1→0 不开放：录入中可能已有成绩，
     * 回到未开始会放开编辑/删除考试。
     */
    private void assertTransitionAllowed(Integer current, int target) {
        int from = current == null ? STATUS_NOT_STARTED : current;
        // ponytail: 2→3 若从 PATCH /status 放行，会跳过待审核校验、排名重算、通知与预警，形成半发布状态
        if (target == STATUS_PUBLISHED) {
            throw new BusinessException("不允许直接将考试置为「已发布」，请通过发布接口发布成绩");
        }
        if (target == from + 1) {
            return;
        }
        boolean adminRollbackOneStep = target == from - 1
                && target >= STATUS_INPUTTING
                && (SecurityUtils.isSuperAdmin() || SecurityUtils.isSchoolAdmin());
        if (adminRollbackOneStep) {
            return;
        }
        throw new BusinessException("不允许从「" + getStatusName(from) + "」变更为「" + getStatusName(target) + "」");
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
        if (existingExam.getStatus() != STATUS_NOT_STARTED) {
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

        responsePage.setRecords(convertToResponses(resultPage.getRecords()));

        return responsePage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void startScoreInput(Long id) {
        updateExamStatus(id, STATUS_INPUTTING); // 状态改为录入中
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitForReview(Long id) {
        Exam exam = examMapper.selectById(id);
        if (exam == null) {
            throw new BusinessException("考试不存在");
        }

        if (exam.getStatus() != STATUS_INPUTTING) {
            throw new BusinessException("只能提交录入中的考试");
        }

        updateExamStatus(id, STATUS_REVIEWING); // 状态改为审核中
    }

    /**
     * 发布考试成绩
     * <p>
     * 发布闭环：待审核校验 → 状态 2→3 → 重算总分与各科排名 → 通知学生 → 成绩预警。
     * ponytail: b~d 与状态变更同处一个事务，任一失败整体回滚，不 catch 吞掉——"已发布但没排名/没通知"
     * 的半发布状态比发布失败更难收拾，失败了让管理员重试即可。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publishExam(Long id) {
        Exam exam = examMapper.selectById(id);
        if (exam == null) {
            throw new BusinessException("考试不存在");
        }

        if (exam.getStatus() == null || exam.getStatus() != STATUS_REVIEWING) {
            throw new BusinessException("只能发布审核中的考试");
        }

        // a. 仍有未通过审核（待审核 / 已驳回）的成绩则拒绝发布
        List<Score> scores = scoreMapper.selectByExamId(SecurityUtils.currentSchoolIdForQuery(), id, null);
        assertAllScoresApproved(scores);

        // ponytail: 直接落库而不走 updateExamStatus——后者现在对 target=3 一律拒绝（见 assertTransitionAllowed）
        writeStatus(exam, STATUS_PUBLISHED); // 状态改为已发布

        // b. 重算排名：一次加载全场成绩，单科三档 + 总分三档各行只写一次（契约 R）
        scoreService.calculateAllRankings(id);

        // c. 通知学生
        notifyStudentsOfPublish(id, scores);

        // d. 成绩预警
        scoreWarningService.checkScoreWarnings(id);
    }

    /**
     * 发布前置校验：所有成绩必须是"已通过"
     * <p>
     * ponytail: 原先只拦 status==0。驳回(2)不会把分数置空，而考试进入审核中后教师已不能重录，
     * 错分会带着 status=2 停在那里；不拦就会进排名与学生通知。status 为 null 视同待审核。
     */
    private void assertAllScoresApproved(List<Score> scores) {
        long rejectedCount = scores.stream()
                .filter(s -> s.getStatus() != null && s.getStatus() == SCORE_STATUS_REJECTED)
                .count();
        long pendingCount = scores.stream()
                .filter(s -> s.getStatus() == null || s.getStatus() != SCORE_STATUS_APPROVED)
                .count() - rejectedCount;
        if (pendingCount == 0 && rejectedCount == 0) {
            return;
        }
        List<String> parts = new ArrayList<>();
        if (pendingCount > 0) {
            parts.add(pendingCount + " 条成绩待审核");
        }
        if (rejectedCount > 0) {
            parts.add(rejectedCount + " 条成绩已驳回");
        }
        throw new BusinessException("仍有 " + String.join("、", parts) + "，不能发布");
    }

    /**
     * 向本场考试有成绩的学生发送成绩发布通知
     * <p>
     * ponytail: sendScorePublishNotification 收的是 sys_user.id 而非 student.id，
     * 必须先经 student.user_id 换算；未开通账号的学生（user_id 为空）没有可投递的对象，跳过。
     */
    private void notifyStudentsOfPublish(Long examId, List<Score> scores) {
        Set<Long> studentIds = scores.stream()
                .map(Score::getStudentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (studentIds.isEmpty()) {
            return;
        }
        List<Long> userIds = studentMapper.selectBatchIds(studentIds).stream()
                .filter(Objects::nonNull)
                .map(Student::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (userIds.isEmpty()) {
            return;
        }
        notificationService.sendScorePublishNotification(examId, userIds);
    }

    /**
     * 转换为响应DTO
     */
    private ExamResponse convertToResponse(Exam exam) {
        return convertToResponses(List.of(exam)).get(0);
    }

    /**
     * 批量转换为响应DTO
     * <p>
     * ponytail: 列表页逐条查学校/年级/学期/科目名会产生 N+1，这里先收齐全部 ID 各查一次再映射。
     * 科目关联仍按考试逐条取：ExamSubjectMapper 只有按单个考试查询的方法，分页量小可接受。
     */
    private List<ExamResponse> convertToResponses(List<Exam> exams) {
        if (exams == null || exams.isEmpty()) {
            return List.of();
        }

        Map<Long, List<ExamSubject>> subjectsByExam = new HashMap<>();
        Map<Long, List<Long>> gradeIdsByExam = new HashMap<>();
        Set<Long> subjectIds = new HashSet<>();
        Set<Long> gradeIds = new HashSet<>();
        Set<Long> schoolIds = new HashSet<>();
        Set<Long> termIds = new HashSet<>();

        for (Exam exam : exams) {
            List<ExamSubject> examSubjects = examSubjectMapper.selectByExamId(exam.getId());
            List<ExamSubject> safeSubjects = examSubjects == null ? List.of() : examSubjects;
            subjectsByExam.put(exam.getId(), safeSubjects);
            safeSubjects.forEach(es -> subjectIds.add(es.getSubjectId()));

            List<Long> parsedGradeIds = parseGradeIds(exam.getGradeIds());
            gradeIdsByExam.put(exam.getId(), parsedGradeIds);
            gradeIds.addAll(parsedGradeIds);

            if (exam.getSchoolId() != null) {
                schoolIds.add(exam.getSchoolId());
            }
            if (exam.getTermId() != null) {
                termIds.add(exam.getTermId());
            }
        }

        Map<Long, Subject> subjects = loadById(subjectIds, subjectMapper::selectBatchIds, Subject::getId);
        Map<Long, Grade> grades = loadById(gradeIds, gradeMapper::selectBatchIds, Grade::getId);
        Map<Long, School> schools = loadById(schoolIds, schoolMapper::selectBatchIds, School::getId);
        Map<Long, AcademicTerm> terms = loadById(termIds, termMapper::selectBatchIds, AcademicTerm::getId);

        return exams.stream()
                .map(exam -> buildResponse(exam,
                        subjectsByExam.getOrDefault(exam.getId(), List.of()),
                        gradeIdsByExam.getOrDefault(exam.getId(), List.of()),
                        subjects, grades, schools, terms))
                .collect(Collectors.toList());
    }

    private ExamResponse buildResponse(Exam exam,
                                       List<ExamSubject> examSubjects,
                                       List<Long> examGradeIds,
                                       Map<Long, Subject> subjects,
                                       Map<Long, Grade> grades,
                                       Map<Long, School> schools,
                                       Map<Long, AcademicTerm> terms) {
        List<ExamResponse.SubjectInfo> subjectInfos = examSubjects.stream()
                .map(es -> {
                    Subject subject = subjects.get(es.getSubjectId());
                    return ExamResponse.SubjectInfo.builder()
                            .subjectId(es.getSubjectId())
                            .subjectName(subject == null ? "" : subject.getSubjectName())
                            .fullScore(es.getFullScore())
                            .sortOrder(es.getSortOrder())
                            .build();
                })
                .collect(Collectors.toList());

        // 按 gradeIds 顺序回填年级名；已删除/不可见的年级跳过而不是留 null
        List<String> gradeNames = examGradeIds.stream()
                .map(grades::get)
                .filter(Objects::nonNull)
                .map(Grade::getGradeName)
                .collect(Collectors.toList());

        School school = exam.getSchoolId() == null ? null : schools.get(exam.getSchoolId());
        AcademicTerm term = exam.getTermId() == null ? null : terms.get(exam.getTermId());

        return ExamResponse.builder()
                .id(exam.getId())
                .schoolId(exam.getSchoolId())
                .schoolName(school == null ? null : school.getSchoolName())
                .termId(exam.getTermId())
                .termName(formatTermName(term))
                .examName(exam.getExamName())
                .examType(exam.getExamType())
                .examTypeName(getExamTypeName(exam.getExamType()))
                .examDate(exam.getExamDate())
                .gradeIds(examGradeIds)
                .gradeNames(gradeNames)
                .status(exam.getStatus())
                .statusName(getStatusName(exam.getStatus()))
                .subjects(subjectInfos)
                .createdAt(exam.getCreatedAt())
                .updatedAt(exam.getUpdatedAt())
                .build();
    }

    private String formatTermName(AcademicTerm term) {
        if (term == null) {
            return null;
        }
        return term.getYear() + " 第" + (Integer.valueOf(1).equals(term.getTerm()) ? "一" : "二") + "学期";
    }

    /**
     * 按 ID 批量加载并建索引；空集合直接返回空 Map，避免 selectBatchIds 生成 IN () 报错
     */
    private <T> Map<Long, T> loadById(Set<Long> ids,
                                      Function<Collection<Long>, List<T>> loader,
                                      Function<T, Long> idGetter) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        List<T> rows = loader.apply(ids);
        if (rows == null) {
            return Collections.emptyMap();
        }
        return rows.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(idGetter, Function.identity(), (a, b) -> a));
    }

    /**
     * 获取考试录入进度
     *
     * 统计交给 ScoreMapper.selectExamProgress 在库内完成：驼峰别名已在 XML 中加双引号，
     * PG 不会折叠成小写，下面按 camelCase 取值才拿得到数据。
     */
    @Override
    public ExamProgressResponse getExamProgress(Long id) {
        Exam exam = examMapper.selectById(id);
        if (exam == null) {
            throw new BusinessException("考试不存在");
        }

        List<Map<String, Object>> rows = scoreMapper.selectExamProgress(id);
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
        resp.setExamId(id);
        resp.setItems(items);
        return resp;
    }

    private Long toLong(Object v) { return v == null ? null : ((Number) v).longValue(); }
    private int toInt(Object v) { return v == null ? 0 : ((Number) v).intValue(); }

    /**
     * 解析年级ID列表
     * 兼容两种历史存储格式：逗号分隔（1,2,3）与 JSON 数组（[1,2,3]）
     */
    private List<Long> parseGradeIds(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.replaceAll("[\\[\\]\\s]", "").split(","))
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .collect(Collectors.toList());
    }

    /**
     * 获取考试类型名称
     */
    private String getExamTypeName(Integer examType) {
        if (examType == null) {
            return null;
        }
        // ponytail: 优先读 sys_dict_data(exam_type) 的 dict_label，让"考试类型"名称可在字典页维护；
        // 字典缺该值、被停用或读取失败时回落到下面的内置表，保证列表/详情始终有文案。
        String label = examTypeLabels().get(String.valueOf(examType));
        if (label != null && !label.isBlank()) {
            return label;
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
     * exam_type 字典的 value → label 快照，60 秒内复用；字典为空也缓存，避免空字典时每行都查库
     */
    private Map<String, String> examTypeLabels() {
        long now = System.currentTimeMillis();
        if (examTypeLabelsLoadedAt > 0 && now - examTypeLabelsLoadedAt < EXAM_TYPE_DICT_TTL_MILLIS) {
            return examTypeLabels;
        }
        Map<String, String> loaded;
        try {
            List<DictData> rows = dictService.getDictDataByType(DICT_EXAM_TYPE);
            loaded = rows == null ? Collections.emptyMap() : rows.stream()
                    .filter(row -> row.getDictValue() != null && row.getDictLabel() != null)
                    .collect(Collectors.toMap(DictData::getDictValue, DictData::getDictLabel, (a, b) -> a));
        } catch (RuntimeException e) {
            log.warn("读取 exam_type 字典失败，考试类型名称回落内置表: {}", e.getMessage());
            loaded = Collections.emptyMap();
        }
        examTypeLabels = Collections.unmodifiableMap(loaded);
        examTypeLabelsLoadedAt = now;
        return examTypeLabels;
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
