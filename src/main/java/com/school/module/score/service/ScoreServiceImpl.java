package com.school.module.score.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.common.exception.BusinessException;
import com.school.common.result.ResultCode;
import com.school.module.class_module.entity.SchoolClass;
import com.school.module.class_module.mapper.ClassMapper;
import com.school.module.exam.entity.Exam;
import com.school.module.exam.entity.ExamSubject;
import com.school.module.exam.mapper.ExamMapper;
import com.school.module.exam.mapper.ExamSubjectMapper;
import com.school.module.score.dto.*;
import com.school.module.score.entity.Score;
import com.school.module.score.mapper.ScoreMapper;
import com.school.module.student.entity.ClassStudent;
import com.school.module.student.entity.Student;
import com.school.module.student.mapper.ClassStudentMapper;
import com.school.module.student.mapper.StudentMapper;
import com.school.module.subject.entity.Subject;
import com.school.module.subject.mapper.SubjectMapper;
import com.school.module.teacher.entity.TeacherClass;
import com.school.module.user.entity.SysUser;
import com.school.module.user.mapper.SysUserMapper;
import com.school.security.service.TeacherScopeService;
import com.school.security.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 成绩服务实现类
 *
 * @author School System
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ScoreServiceImpl implements ScoreService {

    private final ScoreMapper scoreMapper;
    private final StudentMapper studentMapper;
    private final ClassStudentMapper classStudentMapper;
    private final ClassMapper classMapper;
    private final ExamMapper examMapper;
    private final ExamSubjectMapper examSubjectMapper;
    private final SubjectMapper subjectMapper;
    private final SysUserMapper sysUserMapper;
    private final TeacherScopeService teacherScopeService;

    private static final List<Integer> VALID_STATUS = Arrays.asList(0, 1, 2);
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int REMARK_MAX_LENGTH = 500;
    private static final int EXAM_STATUS_PUBLISHED = 3;
    private static final int SCORE_STATUS_REJECTED = 2;
    /** 驳回原因暂存于 remark 的前缀标记（Score 暂无 reject_reason 列） */
    private static final String REJECT_REMARK_PREFIX = "【驳回】";
    /** 无班级归属的脏数据在排名分组时的占位键 */
    private static final long NO_CLASS_KEY = Long.MIN_VALUE;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScoreResponse inputScore(ScoreInputRequest request) {
        boolean isAbsent = request.getIsAbsent() != null && request.getIsAbsent() == 1;
        BigDecimal scoreVal = isAbsent ? null : request.getScore();
        if (!isAbsent && scoreVal == null) {
            throw new BusinessException("未缺考时必须填写分数");
        }

        // 先解析归属学校，upsert 查询和写入都用同一个值
        Long schoolId = SecurityUtils.resolveSchoolId(request.getSchoolId());

        // 教师只能为自己任教的(班级+科目)组合录入成绩
        checkTeacherScope(request.getClassId(), request.getSubjectId());

        // ponytail: 录入前校验考试状态/科目范围/分数范围/班级归属，已发布(status=3)的考试在这里一并被拒绝
        validateInputTarget(request, schoolId, scoreVal);

        // ponytail: upsert — frontend save/auto-save re-calls this endpoint
        Score existing = scoreMapper.selectByExamStudentSubject(
                schoolId, request.getExamId(), request.getStudentId(), request.getSubjectId());

        if (existing != null) {
            existing.setScore(scoreVal);
            existing.setIsAbsent(isAbsent ? 1 : 0);
            existing.setRemark(request.getRemark());
            // ponytail: re-entering a score resets review status to pending
            existing.setStatus(0);
            existing.setReviewedAt(null);
            scoreMapper.updateById(existing);
            return convertToResponse(existing);
        }

        Score score = new Score();
        score.setExamId(request.getExamId());
        score.setStudentId(request.getStudentId());
        score.setSubjectId(request.getSubjectId());
        score.setClassId(request.getClassId());
        score.setSchoolId(schoolId);
        score.setRecorderId(SecurityUtils.currentUserId());
        score.setScore(scoreVal);
        score.setIsAbsent(isAbsent ? 1 : 0);
        // ponytail: 新增路径此前漏写 remark，只有更新路径写了
        score.setRemark(request.getRemark());
        score.setStatus(0);
        score.setRecordedAt(LocalDateTime.now());
        scoreMapper.insert(score);

        return convertToResponse(score);
    }

    /**
     * 录入目标校验：考试存在且录入中、科目属于该考试、分数在 0~满分 内、学生属于该班级
     */
    private void validateInputTarget(ScoreInputRequest request, Long schoolId, BigDecimal scoreVal) {
        Exam exam = examMapper.selectById(request.getExamId());
        if (exam == null) {
            throw new BusinessException(ResultCode.EXAM_NOT_FOUND, "考试不存在");
        }
        if (exam.getSchoolId() != null && !exam.getSchoolId().equals(schoolId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "考试不属于该学校");
        }
        if (exam.getStatus() == null || exam.getStatus() != 1) {
            throw new BusinessException("考试不在录入中状态，当前状态：" + getExamStatusName(exam.getStatus()));
        }

        ExamSubject examSubject = examSubjectMapper.selectByExamId(request.getExamId()).stream()
                .filter(es -> request.getSubjectId().equals(es.getSubjectId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("该科目不在本次考试范围内"));

        if (scoreVal != null) {
            BigDecimal fullScore = examSubject.getFullScore();
            boolean belowZero = scoreVal.compareTo(BigDecimal.ZERO) < 0;
            boolean aboveFull = fullScore != null && scoreVal.compareTo(fullScore) > 0;
            if (belowZero || aboveFull) {
                String upper = fullScore != null ? fullScore.stripTrailingZeros().toPlainString() : "满分";
                throw new BusinessException(ResultCode.SCORE_OUT_OF_RANGE, "分数超出范围（0~" + upper + "）");
            }
        }

        Long inClass = classStudentMapper.selectCount(new LambdaQueryWrapper<ClassStudent>()
                .eq(ClassStudent::getClassId, request.getClassId())
                .eq(ClassStudent::getStudentId, request.getStudentId()));
        if (inClass == null || inClass == 0) {
            throw new BusinessException(ResultCode.STUDENT_NOT_IN_CLASS, "学生不在该班级");
        }
    }

    private String getExamStatusName(Integer status) {
        if (status == null) return "未知";
        return switch (status) {
            case 0 -> "未开始";
            case 1 -> "录入中";
            case 2 -> "审核中";
            case 3 -> "已发布";
            default -> "未知";
        };
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchInputResult batchInputScores(ScoreBatchInputRequest request) {
        int successCount = 0;
        List<BatchInputResult.Failure> failures = new ArrayList<>();

        for (ScoreBatchInputRequest.ScoreItem item : request.getScores()) {
            // ponytail: 契约 1——单项 classId/subjectId 为空时回退到顶层默认值
            Long classId = item.getClassId() != null ? item.getClassId() : request.getClassId();
            Long subjectId = item.getSubjectId() != null ? item.getSubjectId() : request.getSubjectId();
            try {
                if (classId == null) {
                    throw new BusinessException(ResultCode.PARAM_MISSING, "班级ID不能为空");
                }
                if (subjectId == null) {
                    throw new BusinessException(ResultCode.PARAM_MISSING, "科目ID不能为空");
                }
                // ponytail: 不再 setSchoolId(null)——超管调用时 resolveSchoolId(null) 会直接抛异常；
                // 学校归属按学生档案解析，顺带校验学生存在（租户插件保证非超管查不到别校学生）
                Student student = studentMapper.selectById(item.getStudentId());
                if (student == null) {
                    throw new BusinessException(ResultCode.DATA_NOT_FOUND, "学生不存在");
                }

                ScoreInputRequest single = new ScoreInputRequest();
                single.setExamId(request.getExamId());
                single.setStudentId(item.getStudentId());
                single.setSubjectId(subjectId);
                single.setClassId(classId);
                single.setSchoolId(student.getSchoolId());
                single.setScore(item.getScore());
                single.setIsAbsent(item.getIsAbsent() != null ? item.getIsAbsent() : 0);
                single.setRemark(item.getRemark());

                // 复用单条录入：与单条保持一致的 upsert 语义与校验
                inputScore(single);
                successCount++;
            } catch (BusinessException e) {
                // ponytail: 单条失败不影响整批，但要记入 failures 而不是只写日志
                // ponytail: B-7——只兜业务异常；SQL 级异常会让 PG 事务进入 aborted 状态，必须直接上抛让整批回滚并给出明确原因
                log.warn("批量录入成绩失败: examId={}, studentId={}, subjectId={}, 原因={}",
                        request.getExamId(), item.getStudentId(), subjectId, e.getMessage());
                failures.add(BatchInputResult.Failure.builder()
                        .studentId(item.getStudentId())
                        .subjectId(subjectId)
                        .reason(e.getMessage())
                        .build());
            }
        }

        return BatchInputResult.builder()
                .successCount(successCount)
                .failCount(failures.size())
                .failures(failures)
                .build();
    }

    @Override
    public ScoreResponse getScoreById(Long id) {
        Score score = scoreMapper.selectById(id);
        if (score == null) {
            throw new BusinessException("成绩不存在");
        }
        // ponytail: B-6——学生只能看自己且已发布考试的成绩、教师限任教/班主任范围，否则递增 id 就能读到他人/未发布成绩
        if (SecurityUtils.isStudent()) {
            if (!SecurityUtils.currentStudentId().equals(score.getStudentId())) {
                throw new BusinessException(ResultCode.FORBIDDEN, "学生只能查询自己的成绩");
            }
            Exam exam = examMapper.selectById(score.getExamId());
            if (exam == null || exam.getStatus() == null || exam.getStatus() != EXAM_STATUS_PUBLISHED) {
                throw new BusinessException(ResultCode.FORBIDDEN, "考试成绩尚未发布");
            }
        } else {
            checkTeacherScope(score.getClassId(), score.getSubjectId());
        }
        return convertToResponse(score);
    }

    @Override
    public Page<ScoreResponse> listScores(ScoreQueryRequest request) {
        // 学生只能查自己的成绩，且只能看已发布考试的成绩
        if (SecurityUtils.isStudent()) {
            // ponytail: score.student_id 是 student.id 而非 sys_user.id，之前用 currentUserId() 混用了 ID 域
            request.setStudentId(SecurityUtils.currentStudentId());
            request.setPublishedOnly(Boolean.TRUE);
        } else {
            // ponytail: publishedOnly 不接受客户端传入，非学生身份一律清掉
            request.setPublishedOnly(null);
        }
        // 教师只能查自己班级的成绩；subjectId 可选（班主任全科可见，任课教师还需校验科目）
        if (SecurityUtils.isTeacher()) {
            checkTeacherScope(request.getClassId(), request.getSubjectId());
        }
        // 构建分页对象
        Page<Score> page = new Page<>(request.getPageNum(), request.getPageSize());

        // 分页查询
        Page<Score> resultPage = scoreMapper.selectPageByQuery(page, request);

        // 转换为响应DTO
        Page<ScoreResponse> responsePage = new Page<>(
                resultPage.getCurrent(),
                resultPage.getSize(),
                resultPage.getTotal()
        );

        List<Score> records = resultPage.getRecords();
        List<ScoreResponse> responses = records.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());

        // enrich name fields
        if (!responses.isEmpty()) {
            Set<Long> examIds = records.stream().map(Score::getExamId).filter(Objects::nonNull).collect(Collectors.toSet());
            Set<Long> classIds = records.stream().map(Score::getClassId).filter(Objects::nonNull).collect(Collectors.toSet());
            Set<Long> subjectIds = records.stream().map(Score::getSubjectId).filter(Objects::nonNull).collect(Collectors.toSet());
            Set<Long> studentIds = records.stream().map(Score::getStudentId).filter(Objects::nonNull).collect(Collectors.toSet());

            Map<Long, String> examNameMap = examIds.isEmpty() ? Map.of()
                    : examMapper.selectBatchIds(examIds).stream().collect(Collectors.toMap(Exam::getId, Exam::getExamName));
            Map<Long, String> classNameMap = classIds.isEmpty() ? Map.of()
                    : classMapper.selectBatchIds(classIds).stream().collect(Collectors.toMap(SchoolClass::getId, SchoolClass::getClassName));
            Map<Long, Subject> subjectMap = subjectIds.isEmpty() ? Map.of()
                    : subjectMapper.selectBatchIds(subjectIds).stream().collect(Collectors.toMap(Subject::getId, s -> s));
            Map<Long, Student> studentMap = studentIds.isEmpty() ? Map.of()
                    : studentMapper.selectBatchIds(studentIds).stream().collect(Collectors.toMap(Student::getId, s -> s));

            responses.forEach(r -> {
                r.setExamName(examNameMap.getOrDefault(r.getExamId(), ""));
                r.setClassName(classNameMap.getOrDefault(r.getClassId(), ""));
                Subject subj = subjectMap.get(r.getSubjectId());
                if (subj != null) {
                    r.setSubjectName(subj.getSubjectName());
                    r.setFullScore(subj.getFullScore());
                }
                Student stu = studentMap.get(r.getStudentId());
                if (stu != null) {
                    r.setStudentName(stu.getName());
                    r.setStudentNo(stu.getStudentNo());
                }
            });
        }

        responsePage.setRecords(responses);

        return responsePage;
    }

    @Override
    public List<ScoreResponse> getStudentExamScores(Long examId, Long studentId) {
        boolean isStudent = SecurityUtils.isStudent();
        // ponytail: 学生只能查自己（student.id 域）且只能看已发布考试
        if (isStudent && !SecurityUtils.currentStudentId().equals(studentId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "学生只能查询自己的成绩");
        }
        ScoreQueryRequest request = ScoreQueryRequest.builder()
                .examId(examId)
                .studentId(studentId)
                .publishedOnly(isStudent ? Boolean.TRUE : null)
                .build();

        Page<Score> page = new Page<>(1, 100);
        Page<Score> resultPage = scoreMapper.selectPageByQuery(page, request);

        return resultPage.getRecords().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reviewScores(ScoreReviewRequest request) {
        // 只有超管、校管理员、班主任可审核；任课老师不能审核
        if (SecurityUtils.isTeacher() && !SecurityUtils.isHeadTeacher()) {
            throw new BusinessException(ResultCode.FORBIDDEN, "任课老师无权审核成绩，请联系班主任或管理员");
        }
        // 校验审核结果
        if (!Arrays.asList(1, 2).contains(request.getResult())) {
            throw new BusinessException("审核结果无效，只能是1（通过）或2（驳回）");
        }

        // 如果是驳回，必须填写原因
        if (request.getResult() == 2 &&
                (request.getRejectReason() == null || request.getRejectReason().isEmpty())) {
            throw new BusinessException("驳回时必须填写原因");
        }

        LocalDateTime now = LocalDateTime.now();

        for (Long scoreId : resolveScoreIds(request)) {
            Score score = scoreMapper.selectById(scoreId);
            if (score == null) {
                continue;
            }

            // 教师只能审核自己任教班级+科目的成绩
            checkTeacherScope(score.getClassId(), score.getSubjectId());

            // 只能审核待审核状态的成绩
            if (score.getStatus() != 0) {
                continue;
            }

            // 构建更新对象
            Score updatedScore = new Score();
            updatedScore.setId(score.getId());
            updatedScore.setExamId(score.getExamId());
            updatedScore.setStudentId(score.getStudentId());
            updatedScore.setSubjectId(score.getSubjectId());
            updatedScore.setClassId(score.getClassId());
            updatedScore.setSchoolId(score.getSchoolId());
            updatedScore.setScore(score.getScore());
            updatedScore.setIsAbsent(score.getIsAbsent());
            updatedScore.setRecorderId(score.getRecorderId());
            updatedScore.setRecordedAt(score.getRecordedAt());
            updatedScore.setStatus(request.getResult());
            updatedScore.setReviewedAt(now);
            if (request.getResult() == SCORE_STATUS_REJECTED) {
                // ponytail: B-10——Score 没有 reject_reason 列，本轮不加列；驳回原因以「【驳回】」前缀写进 remark 作临时留痕，
                // getReviewHistory 从这里读回 reason；后续加列后应迁回独立字段
                updatedScore.setRemark(truncate(REJECT_REMARK_PREFIX + request.getRejectReason(), REMARK_MAX_LENGTH));
            }

            scoreMapper.updateById(updatedScore);
        }
    }

    /**
     * 解析待审核的成绩ID：优先显式 scoreIds；否则按 考试/班级/科目 三元组查出该组合下所有待审核成绩
     */
    private List<Long> resolveScoreIds(ScoreReviewRequest request) {
        if (request.hasScoreIds()) {
            return request.getScoreIds();
        }
        if (!request.hasTriplet()) {
            throw new BusinessException("请提供成绩ID列表，或完整的考试ID/班级ID/科目ID");
        }

        // ponytail: 复用 selectByExamId(examId, classId) 再按科目/状态内存过滤，一个班一次考试几百行以内，不值得加新 SQL
        // ponytail: B-2——超管 schoolId 为 null，currentSchoolId() 直接抛 FORBIDDEN；与 calculateRankings 一致按 exam.school_id 分流
        Long schoolId = resolveExamSchoolId(request.getExamId());
        List<Long> pendingIds = scoreMapper.selectByExamId(schoolId, request.getExamId(), request.getClassId()).stream()
                .filter(s -> request.getSubjectId().equals(s.getSubjectId()))
                .filter(s -> s.getStatus() != null && s.getStatus() == 0)
                .map(Score::getId)
                .collect(Collectors.toList());

        if (pendingIds.isEmpty()) {
            throw new BusinessException("该考试/班级/科目下没有待审核的成绩");
        }
        return pendingIds;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void calculateRankings(Long examId, Long subjectId) {
        // ponytail: 契约 R——subjectId 非空只写该科 rank_in_*，为空只写 total_rank_*；总分与单科不再共用同一组列
        if (subjectId != null) {
            recalculateRankings(examId, subjectId, true, false);
        } else {
            recalculateRankings(examId, null, false, true);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void calculateAllRankings(Long examId) {
        // ponytail: 契约 R——一次加载全场成绩，单科三档 + 总分三档内存算完后每行只写一次
        recalculateRankings(examId, null, true, true);
    }

    /**
     * 排名计算主干
     *
     * @param subjectId         非空时只计算该科的单科排名
     * @param writeSubjectRanks 是否写 rank_in_*（该行所属科目的单科名次）
     * @param writeTotalRanks   是否写 total_rank_*（该学生本场考试总分名次，同一学生所有行相同）
     */
    private void recalculateRankings(Long examId, Long subjectId, boolean writeSubjectRanks, boolean writeTotalRanks) {
        List<Score> scores = scoreMapper.selectByExamId(resolveExamSchoolId(examId), examId, null);
        if (scores.isEmpty()) {
            return;
        }

        // 缺考/空分的行不计入任何名次；总分只累计有效分数
        List<Score> validScores = scores.stream()
                .filter(ScoreServiceImpl::hasValidScore)
                .collect(Collectors.toList());
        if (validScores.isEmpty()) {
            return;
        }

        Map<Long, Long> classGradeMap = loadClassGradeMap(scores);
        Map<Long, RankTriple> subjectRankByScoreId = writeSubjectRanks
                ? computeSubjectRanks(validScores, subjectId, classGradeMap) : Map.of();
        Map<Long, RankTriple> totalRankByStudentId = writeTotalRanks
                ? computeTotalRanks(validScores, classGradeMap) : Map.of();

        // ponytail: B-1/B-8——每行只 new 一个仅含 id + 排名列的对象写一次；不拷贝旧对象，旧排名不会把刚算好的列覆盖回去
        for (Score score : scores) {
            RankTriple subjectRank = subjectRankByScoreId.get(score.getId());
            RankTriple totalRank = totalRankByStudentId.get(score.getStudentId());
            if (subjectRank == null && totalRank == null) {
                continue;
            }
            scoreMapper.updateById(buildRankUpdate(score.getId(), subjectRank, totalRank));
        }
    }

    /**
     * 只含 id + 本次要写的排名列的更新对象（NOT_NULL 策略下其余列保持不变）
     */
    private static Score buildRankUpdate(Long scoreId, RankTriple subjectRank, RankTriple totalRank) {
        Score update = new Score();
        update.setId(scoreId);
        if (subjectRank != null) {
            update.setRankInClass(subjectRank.inClass());
            update.setRankInGrade(subjectRank.inGrade());
            update.setRankInSchool(subjectRank.inSchool());
        }
        if (totalRank != null) {
            update.setTotalRankInClass(totalRank.inClass());
            update.setTotalRankInGrade(totalRank.inGrade());
            update.setTotalRankInSchool(totalRank.inSchool());
        }
        return update;
    }

    /**
     * 排名/审核等写操作的学校定位：一场考试只属于一所学校，超管按 exam.school_id，其余按当前账号学校。
     * 写路径不用 ...ForQuery()——超管会得到 null 放开全库，不允许一次跨校混算。
     */
    private Long resolveExamSchoolId(Long examId) {
        if (SecurityUtils.isSuperAdmin()) {
            Exam exam = examMapper.selectById(examId);
            if (exam == null) {
                throw new BusinessException(ResultCode.EXAM_NOT_FOUND);
            }
            return exam.getSchoolId();
        }
        return SecurityUtils.currentSchoolId();
    }

    private static boolean hasValidScore(Score s) {
        return (s.getIsAbsent() == null || s.getIsAbsent() == 0) && s.getScore() != null;
    }

    private static Long classKey(Score s) {
        return s.getClassId() != null ? s.getClassId() : NO_CLASS_KEY;
    }

    /**
     * 班级 → 年级映射；查不到年级的班级在 {@link #resolveGradeKey} 里退回按班级自成一组
     */
    private Map<Long, Long> loadClassGradeMap(List<Score> scores) {
        Set<Long> classIds = scores.stream().map(Score::getClassId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (classIds.isEmpty()) {
            return Map.of();
        }
        return classMapper.selectBatchIds(classIds).stream()
                .filter(c -> c.getGradeId() != null)
                .collect(Collectors.toMap(SchoolClass::getId, SchoolClass::getGradeId, (a, b) -> a));
    }

    /**
     * 年级分组键：班级所属 gradeId；查不到年级时用 -classId 让该班自成一组；无班级的脏数据单独一组
     */
    private static Long resolveGradeKey(Long classKey, Map<Long, Long> classGradeMap) {
        if (classKey == null || classKey == NO_CLASS_KEY) {
            return NO_CLASS_KEY;
        }
        Long gradeId = classGradeMap.get(classKey);
        return gradeId != null ? gradeId : Long.valueOf(-classKey);
    }

    /**
     * 单科三档：按科目分组，组内分别按班级 / 年级 / 全校排名；键为成绩行 id
     */
    private Map<Long, RankTriple> computeSubjectRanks(List<Score> validScores, Long subjectId, Map<Long, Long> classGradeMap) {
        Map<Long, List<Score>> bySubject = validScores.stream()
                .filter(s -> s.getSubjectId() != null)
                .filter(s -> subjectId == null || subjectId.equals(s.getSubjectId()))
                .collect(Collectors.groupingBy(Score::getSubjectId));

        Map<Long, RankTriple> result = new HashMap<>();
        for (List<Score> subjectScores : bySubject.values()) {
            Map<Long, Integer> classRank = rankWithinGroups(subjectScores, ScoreServiceImpl::classKey);
            Map<Long, Integer> gradeRank = rankWithinGroups(subjectScores, s -> resolveGradeKey(classKey(s), classGradeMap));
            Map<Long, Integer> schoolRank = rankDescending(toScoreById(subjectScores));
            for (Score s : subjectScores) {
                result.put(s.getId(), new RankTriple(classRank.get(s.getId()), gradeRank.get(s.getId()), schoolRank.get(s.getId())));
            }
        }
        return result;
    }

    /**
     * 总分三档：按学生汇总有效分数，再分别按班级 / 年级 / 全校排名；键为学生 id
     */
    private Map<Long, RankTriple> computeTotalRanks(List<Score> validScores, Map<Long, Long> classGradeMap) {
        Map<Long, BigDecimal> totalByStudent = validScores.stream()
                .collect(Collectors.groupingBy(Score::getStudentId,
                        Collectors.reducing(BigDecimal.ZERO, Score::getScore, BigDecimal::add)));
        // 学生所在班级取其任一成绩行的 class_id
        Map<Long, Long> classByStudent = validScores.stream()
                .collect(Collectors.toMap(Score::getStudentId, ScoreServiceImpl::classKey, (a, b) -> a));

        Map<Long, Integer> classRank = rankTotalsWithinGroups(totalByStudent, classByStudent::get);
        Map<Long, Integer> gradeRank = rankTotalsWithinGroups(totalByStudent,
                studentId -> resolveGradeKey(classByStudent.get(studentId), classGradeMap));
        Map<Long, Integer> schoolRank = rankDescending(totalByStudent);

        Map<Long, RankTriple> result = new HashMap<>();
        for (Long studentId : totalByStudent.keySet()) {
            result.put(studentId, new RankTriple(classRank.get(studentId), gradeRank.get(studentId), schoolRank.get(studentId)));
        }
        return result;
    }

    /**
     * 成绩行按分组键分组，组内按分数降序排名；键为成绩行 id
     */
    private static Map<Long, Integer> rankWithinGroups(List<Score> scores, Function<Score, Long> groupKey) {
        Map<Long, Integer> ranks = new HashMap<>();
        scores.stream()
                .collect(Collectors.groupingBy(groupKey))
                .values()
                .forEach(group -> ranks.putAll(rankDescending(toScoreById(group))));
        return ranks;
    }

    /**
     * 学生总分按分组键分组，组内按总分降序排名；键为学生 id
     */
    private static Map<Long, Integer> rankTotalsWithinGroups(Map<Long, BigDecimal> totalByStudent, Function<Long, Long> groupKey) {
        Map<Long, Map<Long, BigDecimal>> groups = new HashMap<>();
        for (Map.Entry<Long, BigDecimal> entry : totalByStudent.entrySet()) {
            groups.computeIfAbsent(groupKey.apply(entry.getKey()), k -> new HashMap<>())
                    .put(entry.getKey(), entry.getValue());
        }
        Map<Long, Integer> ranks = new HashMap<>();
        groups.values().forEach(group -> ranks.putAll(rankDescending(group)));
        return ranks;
    }

    private static Map<Long, BigDecimal> toScoreById(List<Score> scores) {
        return scores.stream().collect(Collectors.toMap(Score::getId, Score::getScore, (a, b) -> a));
    }

    /**
     * 按分数降序排名，并列同名次、其后跳号（1,1,3）
     */
    private static <K> Map<K, Integer> rankDescending(Map<K, BigDecimal> scoreByKey) {
        List<Map.Entry<K, BigDecimal>> sorted = scoreByKey.entrySet().stream()
                .sorted(Map.Entry.<K, BigDecimal>comparingByValue().reversed())
                .collect(Collectors.toList());
        Map<K, Integer> ranks = new HashMap<>();
        int position = 0;
        int rank = 0;
        BigDecimal last = null;
        for (Map.Entry<K, BigDecimal> entry : sorted) {
            position++;
            if (last == null || entry.getValue().compareTo(last) != 0) {
                rank = position;
                last = entry.getValue();
            }
            ranks.put(entry.getKey(), rank);
        }
        return ranks;
    }

    /**
     * 一行成绩 / 一个学生的三档名次（班级 / 年级 / 全校）
     */
    private record RankTriple(Integer inClass, Integer inGrade, Integer inSchool) {
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteScore(Long id) {
        Score score = scoreMapper.selectById(id);
        if (score == null) {
            throw new BusinessException("成绩不存在");
        }

        // 教师只能删除自己任教班级+科目的成绩
        checkTeacherScope(score.getClassId(), score.getSubjectId());

        // 只能删除待审核或已驳回的成绩
        if (score.getStatus() == 1) {
            throw new BusinessException("已审核通过的成绩不能删除");
        }

        // ponytail: 已发布(status=3)考试的成绩禁止删除，与 inputScore 的「不在录入中不可覆盖」对称
        Exam exam = examMapper.selectById(score.getExamId());
        if (exam != null && exam.getStatus() != null && exam.getStatus() == 3) {
            throw new BusinessException(ResultCode.SCORE_ALREADY_PUBLISHED, "考试已发布，成绩不允许删除");
        }

        scoreMapper.deleteById(id);
    }

    /**
     * 转换为响应DTO
     */
    private ScoreResponse convertToResponse(Score score) {
        return ScoreResponse.builder()
                .id(score.getId())
                .examId(score.getExamId())
                .studentId(score.getStudentId())
                .subjectId(score.getSubjectId())
                .classId(score.getClassId())
                .schoolId(score.getSchoolId())
                .score(score.getScore())
                .isAbsent(score.getIsAbsent())
                .rankInClass(score.getRankInClass())
                .rankInGrade(score.getRankInGrade())
                .rankInSchool(score.getRankInSchool())
                .totalRankInClass(score.getTotalRankInClass())
                .totalRankInGrade(score.getTotalRankInGrade())
                .totalRankInSchool(score.getTotalRankInSchool())
                .recorderId(score.getRecorderId())
                .status(score.getStatus())
                .statusName(getStatusName(score.getStatus()))
                .recordedAt(score.getRecordedAt())
                .reviewedAt(score.getReviewedAt())
                .remark(score.getRemark())
                .createdAt(score.getCreatedAt())
                .updatedAt(score.getUpdatedAt())
                .build();
    }

    /**
     * 获取状态名称
     */
    private String getStatusName(Integer status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case 0 -> "待审核";
            case 1 -> "已通过";
            case 2 -> "已驳回";
            default -> "未知";
        };
    }

    private String toStatusString(Integer status) {
        if (status == null) return "pending";
        return switch (status) {
            case 1 -> "approved";
            case 2 -> "rejected";
            default -> "pending";
        };
    }

    @Override
    public List<ClassScoreItemDto> getClassScoreList(Long examId, Long classId, Long subjectId) {
        checkTeacherScope(classId, subjectId);
        // ponytail: load ALL class students so teachers can enter scores for first time
        List<Long> allStudentIds = classStudentMapper.selectStudentIdsByClassId(classId);
        if (allStudentIds.isEmpty()) {
            return List.of();
        }

        Map<Long, Student> studentMap = studentMapper.selectBatchIds(allStudentIds).stream()
                .collect(Collectors.toMap(Student::getId, s -> s));

        // ponytail: D-ai-5——subjectId 为空时返回该班全部科目的成绩项（每个学生每科一行）；非空时保持每个学生一行
        List<Score> classScores = scoreMapper.selectByExamId(SecurityUtils.currentSchoolIdForQuery(), examId, classId).stream()
                .filter(s -> subjectId == null || subjectId.equals(s.getSubjectId()))
                .collect(Collectors.toList());
        Map<Long, List<Score>> scoresByStudent = classScores.stream()
                .collect(Collectors.groupingBy(Score::getStudentId));
        Map<Long, String> subjectNameMap = subjectId == null ? loadSubjectNames(classScores) : Map.of();

        List<ClassScoreItemDto> result = new ArrayList<>();
        for (Long sid : allStudentIds) {
            Student st = studentMap.get(sid);
            List<Score> rows = scoresByStudent.getOrDefault(sid, List.of());
            if (rows.isEmpty()) {
                result.add(toClassScoreItem(sid, st, null, subjectId, subjectNameMap));
            } else if (subjectId != null) {
                result.add(toClassScoreItem(sid, st, rows.get(0), subjectId, subjectNameMap));
            } else {
                rows.stream()
                        .sorted(Comparator.comparing(Score::getSubjectId, Comparator.nullsLast(Long::compare)))
                        .forEach(s -> result.add(toClassScoreItem(sid, st, s, s.getSubjectId(), subjectNameMap)));
            }
        }
        return result;
    }

    private Map<Long, String> loadSubjectNames(List<Score> scores) {
        Set<Long> subjectIds = scores.stream().map(Score::getSubjectId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (subjectIds.isEmpty()) {
            return Map.of();
        }
        return subjectMapper.selectBatchIds(subjectIds).stream()
                .collect(Collectors.toMap(Subject::getId, Subject::getSubjectName, (a, b) -> a));
    }

    private static ClassScoreItemDto toClassScoreItem(Long studentId, Student st, Score s, Long subjectId,
                                                      Map<Long, String> subjectNameMap) {
        ClassScoreItemDto dto = new ClassScoreItemDto();
        dto.setStudentId(studentId);
        dto.setSubjectId(subjectId);
        dto.setSubjectName(subjectId != null ? subjectNameMap.get(subjectId) : null);
        dto.setStudentNo(st != null ? st.getStudentNo() : "");
        dto.setStudentName(st != null ? st.getName() : "");
        if (s != null) {
            dto.setId(s.getId());
            dto.setScore(s.getScore());
            dto.setIsAbsent(s.getIsAbsent() != null && s.getIsAbsent() == 1);
            dto.setRemark(s.getRemark());
            dto.setRankInClass(s.getRankInClass());
            dto.setRankInGrade(s.getRankInGrade());
            dto.setRankInSchool(s.getRankInSchool());
        }
        return dto;
    }

    @Override
    public ScoreStatisticsDto getScoreStatistics(Long examId, Long classId, Long subjectId) {
        checkTeacherScope(classId, subjectId);
        List<Score> scores = scoreMapper.selectByExamId(SecurityUtils.currentSchoolIdForQuery(), examId, classId, subjectId);

        int totalCount = classStudentMapper.selectStudentIdsByClassId(classId).size();
        int enteredCount = scores.size();

        List<BigDecimal> validScores = scores.stream()
                .filter(s -> s.getIsAbsent() == null || s.getIsAbsent() == 0)
                .filter(s -> s.getScore() != null)
                .map(Score::getScore)
                .collect(Collectors.toList());

        // ponytail: 及格线从 exam_subject 没有字段，用 subject.passScore；拿不到就 60%
        Subject subject = subjectMapper.selectById(subjectId);
        double passLine = (subject != null && subject.getPassScore() != null)
                ? subject.getPassScore().doubleValue() : 60.0;
        double fullScore = (subject != null && subject.getFullScore() != null)
                ? subject.getFullScore().doubleValue() : 100.0;

        ScoreStatisticsDto dto = new ScoreStatisticsDto();
        dto.setTotalCount(totalCount);
        dto.setEnteredCount(enteredCount);

        if (validScores.isEmpty()) {
            return dto;
        }

        double avg = validScores.stream().mapToDouble(BigDecimal::doubleValue).average().orElse(0);
        double max = validScores.stream().mapToDouble(BigDecimal::doubleValue).max().orElse(0);
        double min = validScores.stream().mapToDouble(BigDecimal::doubleValue).min().orElse(0);
        long passCount = validScores.stream().filter(v -> v.doubleValue() >= passLine).count();
        long excellentCount = validScores.stream().filter(v -> v.doubleValue() >= fullScore * 0.85).count();

        dto.setAverageScore(round(avg));
        dto.setHighestScore(max);
        dto.setLowestScore(min);
        dto.setPassRate(round(100.0 * passCount / validScores.size()));
        dto.setExcellentRate(round(100.0 * excellentCount / validScores.size()));
        return dto;
    }

    private double round(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    @Override
    public Page<ReviewListItemDto> getReviewList(Long examId, Long classId, Long subjectId,
                                                  String status, Long pageNum, Long pageSize) {
        checkTeacherScope(classId, subjectId);
        // ponytail: B-3——回到 selectReviewList 的 SQL GROUP BY 聚合并在 SQL 里分页；
        // 之前 selectPageByQuery(1,10000) 会被 PaginationInnerInterceptor.maxLimit=1000 截断，分组大量丢失、total 偏小
        Integer statusInt = status != null && !status.isEmpty() ? toStatusInt(status) : null;
        Long schoolId = SecurityUtils.currentSchoolIdForQuery();
        long current = pageNum != null && pageNum > 0 ? pageNum : 1L;
        long size = pageSize != null && pageSize > 0 ? pageSize : 10L;
        long offset = (current - 1) * size;

        long total = scoreMapper.selectReviewListCount(examId, classId, subjectId, schoolId, statusInt);
        Page<ReviewListItemDto> result = new Page<>(current, size, total);
        if (total == 0 || offset >= total) {
            result.setRecords(List.of());
            return result;
        }

        List<Map<String, Object>> rows = scoreMapper.selectReviewList(
                examId, classId, subjectId, schoolId, statusInt, size, offset);

        Set<Long> recorderIds = rows.stream()
                .map(r -> toLong(r.get("recorderId"))).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> recorderNameMap = recorderIds.isEmpty() ? Map.of()
                : sysUserMapper.selectBatchIds(recorderIds).stream()
                        .collect(Collectors.toMap(SysUser::getId,
                                u -> u.getRealName() != null ? u.getRealName() : u.getUsername()));

        List<ReviewListItemDto> dtos = rows.stream().map(r -> {
            ReviewListItemDto dto = new ReviewListItemDto();
            dto.setId(toLong(r.get("id")));
            dto.setExamId(toLong(r.get("examId")));
            dto.setExamName(Objects.toString(r.get("examName"), ""));
            dto.setClassId(toLong(r.get("classId")));
            dto.setClassName(Objects.toString(r.get("className"), ""));
            dto.setSubjectId(toLong(r.get("subjectId")));
            dto.setSubjectName(Objects.toString(r.get("subjectName"), ""));
            Object fullScore = r.get("fullScore");
            dto.setFullScore(fullScore instanceof Number fs ? fs.doubleValue() : 100.0);
            dto.setInputPerson(recorderNameMap.getOrDefault(toLong(r.get("recorderId")), ""));
            dto.setInputTime(formatTime(r.get("inputTime"), ""));
            Object groupStatus = r.get("status");
            dto.setStatus(toStatusString(groupStatus instanceof Number gs ? gs.intValue() : null));
            dto.setReviewTime(formatTime(r.get("reviewTime"), null));
            return dto;
        }).collect(Collectors.toList());

        result.setRecords(dtos);
        return result;
    }

    private static Long toLong(Object value) {
        return value instanceof Number n ? n.longValue() : null;
    }

    /**
     * resultType=map 的时间列可能是 java.sql.Timestamp 或 LocalDateTime，统一格式化
     */
    private static String formatTime(Object value, String fallback) {
        if (value instanceof Timestamp ts) {
            return ts.toLocalDateTime().format(DT_FMT);
        }
        if (value instanceof LocalDateTime ldt) {
            return ldt.format(DT_FMT);
        }
        return fallback;
    }

    private Integer toStatusInt(String status) {
        return switch (status) {
            case "approved" -> 1;
            case "rejected" -> 2;
            default -> 0;
        };
    }

    @Override
    public ReviewDetailDto getReviewDetail(Long examId, Long classId, Long subjectId) {
        checkTeacherScope(classId, subjectId);
        Exam exam = examMapper.selectById(examId);
        SchoolClass cls = classMapper.selectById(classId);
        Subject subject = subjectMapper.selectById(subjectId);

        List<Score> scores = scoreMapper.selectByExamId(SecurityUtils.currentSchoolIdForQuery(), examId, classId, subjectId);

        // 获取学生信息
        List<Long> studentIds = scores.stream().map(Score::getStudentId).collect(Collectors.toList());
        Map<Long, Student> studentMap = studentIds.isEmpty() ? Map.of()
                : studentMapper.selectBatchIds(studentIds).stream()
                        .collect(Collectors.toMap(Student::getId, s -> s));

        // ExamSubject 满分
        List<ExamSubject> examSubjects = examSubjectMapper.selectByExamId(examId);
        double fullScore = examSubjects.stream()
                .filter(es -> subjectId.equals(es.getSubjectId()))
                .findFirst()
                .map(es -> es.getFullScore() != null ? es.getFullScore().doubleValue() : 100.0)
                .orElse(subject != null && subject.getFullScore() != null ? subject.getFullScore().doubleValue() : 100.0);

        // 整组状态：取最多的状态
        Integer groupStatus = scores.stream()
                .collect(Collectors.groupingBy(s -> s.getStatus() != null ? s.getStatus() : 0, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(0);

        // 统计
        List<BigDecimal> validScores = scores.stream()
                .filter(s -> s.getIsAbsent() == null || s.getIsAbsent() == 0)
                .filter(s -> s.getScore() != null)
                .map(Score::getScore)
                .collect(Collectors.toList());

        int totalCount = classStudentMapper.selectStudentIdsByClassId(classId).size();
        double passLine = (subject != null && subject.getPassScore() != null)
                ? subject.getPassScore().doubleValue() : 60.0;

        ReviewDetailDto.Statistics stats = new ReviewDetailDto.Statistics();
        stats.setTotalCount(totalCount);
        stats.setEnteredCount(scores.size());
        if (!validScores.isEmpty()) {
            double avg = validScores.stream().mapToDouble(BigDecimal::doubleValue).average().orElse(0);
            double max = validScores.stream().mapToDouble(BigDecimal::doubleValue).max().orElse(0);
            double min = validScores.stream().mapToDouble(BigDecimal::doubleValue).min().orElse(0);
            long passCount = validScores.stream().filter(v -> v.doubleValue() >= passLine).count();
            long excellentCount = validScores.stream().filter(v -> v.doubleValue() >= fullScore * 0.85).count();
            stats.setAverageScore(round(avg));
            stats.setHighestScore(max);
            stats.setLowestScore(min);
            stats.setPassRate(round(100.0 * passCount / validScores.size()));
            stats.setExcellentRate(round(100.0 * excellentCount / validScores.size()));
        }

        // 成绩列表
        List<ClassScoreItemDto> scoreDtos = scores.stream().map(s -> {
            ClassScoreItemDto dto = new ClassScoreItemDto();
            dto.setId(s.getId());
            dto.setStudentId(s.getStudentId());
            Student st = studentMap.get(s.getStudentId());
            dto.setStudentNo(st != null ? st.getStudentNo() : "");
            dto.setStudentName(st != null ? st.getName() : "");
            dto.setScore(s.getScore());
            dto.setIsAbsent(s.getIsAbsent() != null && s.getIsAbsent() == 1);
            // ponytail: B-10——驳回原因暂存在 remark，审核详情一并回传
            dto.setRemark(s.getRemark());
            return dto;
        }).collect(Collectors.toList());

        // 录入时间取最早一条
        String inputTime = scores.stream()
                .filter(s -> s.getRecordedAt() != null)
                .map(Score::getRecordedAt)
                .min(LocalDateTime::compareTo)
                .map(t -> t.format(DT_FMT))
                .orElse("");

        ReviewDetailDto detail = new ReviewDetailDto();

        ReviewDetailDto.ExamInfo examInfo = new ReviewDetailDto.ExamInfo();
        examInfo.setExamId(examId);
        examInfo.setExamName(exam != null ? exam.getExamName() : "");
        examInfo.setExamType(exam != null ? getExamTypeName(exam.getExamType()) : "");
        examInfo.setExamDate(exam != null && exam.getExamDate() != null ? exam.getExamDate().toString() : "");
        detail.setExamInfo(examInfo);

        ReviewDetailDto.ClassInfo classInfo = new ReviewDetailDto.ClassInfo();
        classInfo.setClassId(classId);
        classInfo.setClassName(cls != null ? cls.getClassName() : "");
        detail.setClassInfo(classInfo);

        ReviewDetailDto.SubjectInfo subjectInfo = new ReviewDetailDto.SubjectInfo();
        subjectInfo.setSubjectId(subjectId);
        subjectInfo.setSubjectName(subject != null ? subject.getSubjectName() : "");
        subjectInfo.setFullScore(fullScore);
        detail.setSubjectInfo(subjectInfo);

        ReviewDetailDto.InputInfo inputInfo = new ReviewDetailDto.InputInfo();
        String recorderName = scores.stream()
                .map(Score::getRecorderId).filter(Objects::nonNull).findFirst()
                .map(rid -> {
                    SysUser u = sysUserMapper.selectById(rid);
                    return u != null ? (u.getRealName() != null ? u.getRealName() : u.getUsername()) : "";
                }).orElse("");
        inputInfo.setInputPerson(recorderName);
        inputInfo.setInputTime(inputTime);
        detail.setInputInfo(inputInfo);

        detail.setStatus(toStatusString(groupStatus));
        detail.setScores(scoreDtos);
        detail.setStatistics(stats);

        return detail;
    }

    private String getExamTypeName(Integer examType) {
        if (examType == null) return "";
        return switch (examType) {
            case 1 -> "月考"; case 2 -> "期中"; case 3 -> "期末";
            case 4 -> "模拟"; case 5 -> "其他"; default -> "未知";
        };
    }

    @Override
    public List<ReviewHistoryDto> getReviewHistory(Long examId, Long classId, Long subjectId) {
        checkTeacherScope(classId, subjectId);
        // ponytail: 无独立审核日志表，从已审核成绩中构造历史条目
        List<Score> reviewed = scoreMapper.selectByExamId(SecurityUtils.currentSchoolIdForQuery(), examId, classId).stream()
                .filter(s -> subjectId.equals(s.getSubjectId()))
                .filter(s -> s.getStatus() != null && s.getStatus() != 0 && s.getReviewedAt() != null)
                .collect(Collectors.toList());

        if (reviewed.isEmpty()) return List.of();

        // 按 reviewedAt 分组（同一次审核时间相近，取整分钟聚合）
        // ponytail: 取最后一次 reviewedAt 最大的那条代表整组
        Score latest = reviewed.stream()
                .max(Comparator.comparing(Score::getReviewedAt))
                .orElse(reviewed.get(0));

        String reviewerName = "";
        if (latest.getRecorderId() != null) {
            SysUser reviewer = sysUserMapper.selectById(latest.getRecorderId());
            if (reviewer != null) {
                reviewerName = reviewer.getRealName() != null ? reviewer.getRealName() : reviewer.getUsername();
            }
        }
        ReviewHistoryDto dto = new ReviewHistoryDto();
        dto.setId(latest.getId());
        dto.setOperator(reviewerName);
        dto.setOperateTime(latest.getReviewedAt().format(DT_FMT));
        dto.setAction(latest.getStatus() == 1 ? "approve" : "reject");
        // ponytail: B-10——驳回原因暂存于 remark 的「【驳回】」前缀段，回传给前端 ReviewHistory.reason
        String remark = latest.getRemark();
        if (latest.getStatus() == SCORE_STATUS_REJECTED && remark != null && remark.startsWith(REJECT_REMARK_PREFIX)) {
            dto.setReason(remark.substring(REJECT_REMARK_PREFIX.length()));
        }
        return List.of(dto);
    }

    @Override
    public List<ScoreDistributionDto> getScoreDistribution(Long examId, Long classId, Long subjectId) {
        checkTeacherScope(classId, subjectId);
        List<BigDecimal> scores = scoreMapper.selectByExamId(SecurityUtils.currentSchoolIdForQuery(), examId, classId).stream()
                .filter(s -> subjectId.equals(s.getSubjectId()))
                .filter(s -> s.getIsAbsent() == null || s.getIsAbsent() == 0)
                .filter(s -> s.getScore() != null)
                .map(Score::getScore)
                .collect(Collectors.toList());

        Subject subject = subjectMapper.selectById(subjectId);
        double fullScore = (subject != null && subject.getFullScore() != null)
                ? subject.getFullScore().doubleValue() : 100.0;

        // 5 buckets relative to fullScore
        int[] counts = new int[5];
        for (BigDecimal s : scores) {
            double pct = s.doubleValue() / fullScore * 100;
            if (pct < 60) counts[0]++;
            else if (pct < 70) counts[1]++;
            else if (pct < 80) counts[2]++;
            else if (pct < 90) counts[3]++;
            else counts[4]++;
        }
        String[] ranges = {"0-59", "60-69", "70-79", "80-89", "90-100"};
        List<ScoreDistributionDto> result = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            result.add(new ScoreDistributionDto(ranges[i], counts[i]));
        }
        return result;
    }

    /**
     * 教师粒度访问检查
     * <p>
     * 非教师账号直接放行。教师账号：
     * - classId 必须在其任教或担任班主任的班级集合内
     * - subjectId 不为 null 时还需满足 isAllowedClassSubject(classId, subjectId)
     *
     * @param classId   必须指定，教师不能查"全校成绩"
     * @param subjectId 可选，为 null 时仅校验班级维度（班主任全科可见）
     */
    private void checkTeacherScope(Long classId, Long subjectId) {
        if (SecurityUtils.isStudent()) {
            throw new BusinessException(ResultCode.FORBIDDEN, "学生账号无权执行此操作");
        }
        if (!SecurityUtils.isTeacher()) return;
        if (classId == null) {
            throw new BusinessException(ResultCode.FORBIDDEN, "教师账号必须指定班级");
        }
        if (subjectId != null) {
            if (!teacherScopeService.isAllowedClassSubject(classId, subjectId)) {
                throw new BusinessException(ResultCode.FORBIDDEN, "无权访问该班级或科目的成绩");
            }
        } else {
            if (!teacherScopeService.currentAllowedClassIds().contains(classId)) {
                throw new BusinessException(ResultCode.FORBIDDEN, "无权访问该班级的成绩");
            }
        }
    }

}

