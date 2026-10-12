package com.school.module.scorechange.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.common.exception.BusinessException;
import com.school.common.result.ResultCode;
import com.school.module.exam.entity.Exam;
import com.school.module.exam.mapper.ExamMapper;
import com.school.module.notification.service.NotificationService;
import com.school.module.score.entity.Score;
import com.school.module.score.mapper.ScoreMapper;
import com.school.module.score.service.ScoreService;
import com.school.module.scorechange.dto.*;
import com.school.module.scorechange.entity.ScoreChangeLog;
import com.school.module.scorechange.mapper.ScoreChangeLogMapper;
import com.school.module.student.entity.Student;
import com.school.module.student.mapper.StudentMapper;
import com.school.module.subject.entity.Subject;
import com.school.module.subject.mapper.SubjectMapper;
import com.school.module.user.entity.SysUser;
import com.school.module.user.mapper.SysUserMapper;
import com.school.security.service.TeacherScopeService;
import com.school.security.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 成绩变更服务实现类
 *
 * @author School System
 */
@Service
@RequiredArgsConstructor
public class ScoreChangeServiceImpl implements ScoreChangeService {

    private final ScoreChangeLogMapper changeLogMapper;
    private final ScoreMapper scoreMapper;
    private final ExamMapper examMapper;
    private final StudentMapper studentMapper;
    private final SubjectMapper subjectMapper;
    private final SysUserMapper sysUserMapper;
    private final ScoreService scoreService;
    private final NotificationService notificationService;
    private final TeacherScopeService teacherScopeService;

    private static final int EXAM_STATUS_PUBLISHED = 3;
    private static final int CHANGE_STATUS_PENDING = 0;
    private static final int CHANGE_STATUS_APPROVED = 1;
    private static final int CHANGE_STATUS_REJECTED = 2;
    /** 通知类型：3-系统通知（见 NotificationServiceImpl.getTypeName） */
    private static final int NOTIFICATION_TYPE_SYSTEM = 3;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScoreChangeLogResponse applyScoreChange(ScoreChangeApplyRequest request, Long applicantId) {
        // 查询成绩
        Score score = scoreMapper.selectById(request.getScoreId());
        if (score == null) {
            throw new BusinessException("成绩不存在");
        }

        // ponytail: "是否需要走审批"看的是考试是否已发布，而不是 score.status：
        // 未发布前成绩本就该在录入页直接改，审批流只服务于已公示的成绩。
        Exam exam = examMapper.selectById(score.getExamId());
        if (exam == null) {
            throw new BusinessException("考试不存在");
        }
        if (exam.getStatus() == null || exam.getStatus() != EXAM_STATUS_PUBLISHED) {
            throw new BusinessException("成绩尚未发布，请直接在录入页修改");
        }

        assertCanApply(score);

        // ponytail: 原实现只翻第 1 页第 1 条且不按 scoreId 过滤，重复申请检查形同虚设；
        // 这里直接按 scoreId + 待审批计数（BaseEntity 的 @TableLogic 会自动追加 deleted=0）。
        Long pendingCount = changeLogMapper.selectCount(new LambdaQueryWrapper<ScoreChangeLog>()
                .eq(ScoreChangeLog::getScoreId, request.getScoreId())
                .eq(ScoreChangeLog::getStatus, CHANGE_STATUS_PENDING));
        if (pendingCount != null && pendingCount > 0) {
            throw new BusinessException("该成绩已有待审批的修改申请，请勿重复提交");
        }

        // 创建变更日志
        ScoreChangeLog changeLog = new ScoreChangeLog();
        changeLog.setScoreId(request.getScoreId());
        changeLog.setExamId(score.getExamId());
        changeLog.setStudentId(score.getStudentId());
        changeLog.setSubjectId(score.getSubjectId());
        changeLog.setOldScore(score.getScore());
        changeLog.setNewScore(request.getNewScore());
        changeLog.setChangeReason(request.getChangeReason());
        changeLog.setApplicantId(applicantId);
        changeLog.setStatus(CHANGE_STATUS_PENDING); // 待审批
        changeLog.setApplyTime(LocalDateTime.now());

        changeLogMapper.insert(changeLog);

        return convertToResponse(changeLog);
    }

    /**
     * 申请权限：管理员，或任教该班级该科目的教师（班主任对本班全科视同任教）
     */
    private void assertCanApply(Score score) {
        if (SecurityUtils.isSuperAdmin() || SecurityUtils.isSchoolAdmin()) {
            return;
        }
        if (SecurityUtils.isTeacher()
                && teacherScopeService.isAllowedClassSubject(score.getClassId(), score.getSubjectId())) {
            return;
        }
        throw new BusinessException(ResultCode.FORBIDDEN, "仅管理员或任教该班级该科目的教师可申请修改成绩");
    }

    /**
     * 审批权限：与 reviewScores 口径一致——任课老师不能审批，只有管理员和班主任可以
     */
    private void assertCanApprove() {
        if (SecurityUtils.isSuperAdmin() || SecurityUtils.isSchoolAdmin() || SecurityUtils.isHeadTeacher()) {
            return;
        }
        throw new BusinessException(ResultCode.FORBIDDEN, "仅管理员或班主任可审批成绩修改申请");
    }

    /**
     * 班主任只能审批自己班级范围内的申请（管理员不受限）
     * <p>
     * ponytail: 原先用 isAllowedClassSubject（任课 OR 班主任），任课老师在自己任教的班也能审批，
     * 与"只有班主任可审批"的口径冲突；现在只认班主任班级。headClassIds 由调用方在循环外查一次传入。
     */
    private void assertApproveScope(Score score, Set<Long> headClassIds) {
        if (SecurityUtils.isSuperAdmin() || SecurityUtils.isSchoolAdmin()) {
            return;
        }
        if (!headClassIds.contains(score.getClassId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权审批该班级的成绩修改申请，仅限本人担任班主任的班级");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveScoreChange(ScoreChangeApproveRequest request, Long approverId) {
        assertCanApprove();

        // 校验审批结果
        if (request.getResult() == null
                || (request.getResult() != CHANGE_STATUS_APPROVED && request.getResult() != CHANGE_STATUS_REJECTED)) {
            throw new BusinessException("审批结果无效，只能是1（通过）或2（驳回）");
        }

        // 驳回时必须填写原因
        if (request.getResult() == CHANGE_STATUS_REJECTED &&
            (request.getRejectReason() == null || request.getRejectReason().isEmpty())) {
            throw new BusinessException("驳回时必须填写原因");
        }

        LocalDateTime now = LocalDateTime.now();
        boolean isAdmin = SecurityUtils.isSuperAdmin() || SecurityUtils.isSchoolAdmin();
        // ponytail: 班主任班级集合在循环外查一次，避免每条申请各查一次 teacher_class
        Set<Long> headClassIds = isAdmin ? Collections.emptySet() : teacherScopeService.currentHeadClassIds();
        Set<Long> examIdsToRerank = new LinkedHashSet<>();

        for (Long changeLogId : request.getChangeLogIds()) {
            ScoreChangeLog changeLog = changeLogMapper.selectById(changeLogId);
            if (changeLog == null) {
                continue;
            }

            // 只能审批待审批状态的申请
            if (changeLog.getStatus() == null || changeLog.getStatus() != CHANGE_STATUS_PENDING) {
                continue;
            }

            // ponytail: 四眼原则——申请人与审批人不能是同一人，管理员也不例外
            if (approverId != null && approverId.equals(changeLog.getApplicantId())) {
                throw new BusinessException(ResultCode.FORBIDDEN, "不能审批自己提交的申请");
            }

            Score score = scoreMapper.selectById(changeLog.getScoreId());
            if (score == null) {
                // ponytail: 成绩已不存在时不再"标记通过但跳过回写"，那会留下一条通过了却没生效的申请
                throw new BusinessException("申请 " + changeLogId + " 对应的成绩不存在，无法审批");
            }

            assertApproveScope(score, headClassIds);

            // 更新变更日志
            ScoreChangeLog updated = new ScoreChangeLog();
            updated.setId(changeLog.getId());
            updated.setScoreId(changeLog.getScoreId());
            updated.setExamId(changeLog.getExamId());
            updated.setStudentId(changeLog.getStudentId());
            updated.setSubjectId(changeLog.getSubjectId());
            updated.setOldScore(changeLog.getOldScore());
            updated.setNewScore(changeLog.getNewScore());
            updated.setChangeReason(changeLog.getChangeReason());
            updated.setApplicantId(changeLog.getApplicantId());
            updated.setApproverId(approverId);
            updated.setStatus(request.getResult());
            updated.setApplyTime(changeLog.getApplyTime());
            updated.setApproveTime(now);
            updated.setRejectReason(request.getRejectReason());

            changeLogMapper.updateById(updated);

            // 如果审批通过，回写成绩、登记待重算排名、通知学生
            if (request.getResult() == CHANGE_STATUS_APPROVED) {
                writeBackScore(score, changeLog);
                examIdsToRerank.add(score.getExamId());
                notifyStudentOfApproval(score, changeLog);
            }
        }

        // ponytail: 改分同时影响该科与总分名次，按考试去重后一次性全量重算（单科三档 + 总分三档），每行只写一次
        for (Long examId : examIdsToRerank) {
            scoreService.calculateAllRankings(examId);
        }
    }

    /**
     * 将审批通过的新分数回写到成绩
     */
    private void writeBackScore(Score score, ScoreChangeLog changeLog) {
        Score updatedScore = new Score();
        updatedScore.setId(score.getId());
        updatedScore.setExamId(score.getExamId());
        updatedScore.setStudentId(score.getStudentId());
        updatedScore.setSubjectId(score.getSubjectId());
        updatedScore.setClassId(score.getClassId());
        updatedScore.setSchoolId(score.getSchoolId());
        updatedScore.setScore(changeLog.getNewScore());
        // 审批给出了实际分数的，不再视为缺考，否则 calculateRankings 会把这条成绩排除在排名之外
        updatedScore.setIsAbsent(changeLog.getNewScore() != null ? 0 : score.getIsAbsent());
        updatedScore.setRankInClass(score.getRankInClass());
        updatedScore.setRankInGrade(score.getRankInGrade());
        updatedScore.setRankInSchool(score.getRankInSchool());
        updatedScore.setRecorderId(score.getRecorderId());
        updatedScore.setStatus(score.getStatus());
        updatedScore.setRecordedAt(score.getRecordedAt());
        updatedScore.setReviewedAt(score.getReviewedAt());
        updatedScore.setRemark(score.getRemark());

        scoreMapper.updateById(updatedScore);
    }

    /**
     * 审批通过后通知学生本人
     * <p>
     * ponytail: 通知投递对象是 sys_user.id，须经 student.user_id 换算；未开通账号的学生没有可投递对象，静默跳过。
     */
    private void notifyStudentOfApproval(Score score, ScoreChangeLog changeLog) {
        Student student = studentMapper.selectById(score.getStudentId());
        if (student == null || student.getUserId() == null) {
            return;
        }
        Exam exam = examMapper.selectById(score.getExamId());
        Subject subject = subjectMapper.selectById(score.getSubjectId());
        String examName = exam == null ? "考试" : exam.getExamName();
        String subjectName = subject == null ? "" : subject.getSubjectName();
        String content = "您在「" + examName + "」的" + subjectName + "成绩已由 "
                + formatScore(changeLog.getOldScore()) + " 修改为 " + formatScore(changeLog.getNewScore())
                + "，修改原因：" + Objects.toString(changeLog.getChangeReason(), "") + "。";

        notificationService.batchCreateNotifications(
                List.of(student.getUserId()), "成绩修改通知", content, NOTIFICATION_TYPE_SYSTEM, score.getExamId());
    }

    private String formatScore(BigDecimal value) {
        return value == null ? "缺考" : value.stripTrailingZeros().toPlainString();
    }

    @Override
    public ScoreChangeLogResponse getChangeLogById(Long id) {
        ScoreChangeLog changeLog = changeLogMapper.selectById(id);
        if (changeLog == null) {
            throw new BusinessException("变更日志不存在");
        }
        assertCanView(changeLog);
        return convertToResponse(changeLog);
    }

    /**
     * 详情归属校验：校管理员只能看本校考试的记录，教师只能看任教/班主任班级的记录
     * <p>
     * ponytail: score_change_log 不在租户白名单，selectById 不会被自动收窄，归属要在这里显式判。
     */
    private void assertCanView(ScoreChangeLog changeLog) {
        if (SecurityUtils.isSuperAdmin()) {
            return;
        }
        if (SecurityUtils.isSchoolAdmin()) {
            Exam exam = examMapper.selectById(changeLog.getExamId());
            if (exam != null && SecurityUtils.currentSchoolId().equals(exam.getSchoolId())) {
                return;
            }
        } else if (SecurityUtils.isTeacher()) {
            Score score = scoreMapper.selectById(changeLog.getScoreId());
            if (score != null && teacherScopeService.currentAllowedClassIds().contains(score.getClassId())) {
                return;
            }
        }
        throw new BusinessException(ResultCode.FORBIDDEN, "无权查看该成绩变更记录");
    }

    /**
     * 列表范围收窄：超管不限；校管理员按本校考试；教师按任教 ∪ 班主任班级
     * <p>
     * ponytail: schoolId/classIds 是服务端范围字段，先清空客户端可能传入的值再按身份填入，
     * 不信任请求参数里的任何范围声明。
     */
    private void applyListScope(ScoreChangeQueryRequest request) {
        request.setSchoolId(null);
        request.setClassIds(null);
        if (SecurityUtils.isSuperAdmin()) {
            return;
        }
        if (SecurityUtils.isSchoolAdmin()) {
            request.setSchoolId(SecurityUtils.currentSchoolId());
            return;
        }
        if (SecurityUtils.isTeacher()) {
            request.setClassIds(teacherScopeService.currentAllowedClassIds());
            return;
        }
        throw new BusinessException(ResultCode.FORBIDDEN, "无权查看成绩变更记录");
    }

    @Override
    public Page<ScoreChangeLogResponse> listChangeLogs(ScoreChangeQueryRequest request) {
        applyListScope(request);
        // ponytail: 教师没有任何班级时直接返回空页，不让 XML 生成 IN () 也不放开全表
        if (request.getClassIds() != null && request.getClassIds().isEmpty()) {
            Page<ScoreChangeLogResponse> empty = new Page<>(request.getPageNum(), request.getPageSize(), 0);
            empty.setRecords(List.of());
            return empty;
        }
        Page<ScoreChangeLog> page = new Page<>(request.getPageNum(), request.getPageSize());
        Page<ScoreChangeLog> resultPage = changeLogMapper.selectPageByQuery(page, request);

        Page<ScoreChangeLogResponse> responsePage = new Page<>(
                resultPage.getCurrent(),
                resultPage.getSize(),
                resultPage.getTotal()
        );

        responsePage.setRecords(convertToResponses(resultPage.getRecords()));
        return responsePage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelApply(Long id, Long applicantId) {
        ScoreChangeLog changeLog = changeLogMapper.selectById(id);
        if (changeLog == null) {
            throw new BusinessException("变更日志不存在");
        }

        // 只能撤销自己的申请
        if (!changeLog.getApplicantId().equals(applicantId)) {
            throw new BusinessException("只能撤销自己的申请");
        }

        // 只能撤销待审批的申请
        if (changeLog.getStatus() != 0) {
            throw new BusinessException("只能撤销待审批的申请");
        }

        changeLogMapper.deleteById(id);
    }

    /**
     * 转换为响应DTO
     */
    private ScoreChangeLogResponse convertToResponse(ScoreChangeLog changeLog) {
        return convertToResponses(List.of(changeLog)).get(0);
    }

    /**
     * 批量转换为响应DTO，回填考试/学生/科目/申请人/审批人名称
     * <p>
     * ponytail: 先收齐全部 ID 各查一次（exam/student/subject/sys_user 共 4 条 SQL），避免列表页 N+1。
     * exam/student/subject 受租户插件按学校过滤，别校数据查不到时对应名称留空而不是报错。
     */
    private List<ScoreChangeLogResponse> convertToResponses(List<ScoreChangeLog> logs) {
        if (logs == null || logs.isEmpty()) {
            return List.of();
        }

        Map<Long, Exam> exams = loadById(collectIds(logs, ScoreChangeLog::getExamId),
                examMapper::selectBatchIds, Exam::getId);
        Map<Long, Student> students = loadById(collectIds(logs, ScoreChangeLog::getStudentId),
                studentMapper::selectBatchIds, Student::getId);
        Map<Long, Subject> subjects = loadById(collectIds(logs, ScoreChangeLog::getSubjectId),
                subjectMapper::selectBatchIds, Subject::getId);

        Set<Long> userIds = collectIds(logs, ScoreChangeLog::getApplicantId);
        userIds.addAll(collectIds(logs, ScoreChangeLog::getApproverId));
        Map<Long, SysUser> users = loadById(userIds, sysUserMapper::selectBatchIds, SysUser::getId);

        return logs.stream()
                .map(log -> {
                    Exam exam = lookup(exams, log.getExamId());
                    Student student = lookup(students, log.getStudentId());
                    Subject subject = lookup(subjects, log.getSubjectId());
                    return ScoreChangeLogResponse.builder()
                            .id(log.getId())
                            .scoreId(log.getScoreId())
                            .examId(log.getExamId())
                            .examName(exam == null ? null : exam.getExamName())
                            .studentId(log.getStudentId())
                            .studentName(student == null ? null : student.getName())
                            .studentNo(student == null ? null : student.getStudentNo())
                            .subjectId(log.getSubjectId())
                            .subjectName(subject == null ? null : subject.getSubjectName())
                            .oldScore(log.getOldScore())
                            .newScore(log.getNewScore())
                            .changeReason(log.getChangeReason())
                            .applicantId(log.getApplicantId())
                            .applicantName(displayName(lookup(users, log.getApplicantId())))
                            .approverId(log.getApproverId())
                            .approverName(displayName(lookup(users, log.getApproverId())))
                            .status(log.getStatus())
                            .statusName(getStatusName(log.getStatus()))
                            .applyTime(log.getApplyTime())
                            .approveTime(log.getApproveTime())
                            .rejectReason(log.getRejectReason())
                            .build();
                })
                .collect(Collectors.toList());
    }

    private Set<Long> collectIds(List<ScoreChangeLog> logs, Function<ScoreChangeLog, Long> getter) {
        return logs.stream()
                .map(getter)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
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

    private <T> T lookup(Map<Long, T> map, Long id) {
        return id == null ? null : map.get(id);
    }

    private String displayName(SysUser user) {
        if (user == null) {
            return null;
        }
        return user.getRealName() != null && !user.getRealName().isBlank() ? user.getRealName() : user.getUsername();
    }

    /**
     * 获取状态名称
     */
    private String getStatusName(Integer status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case 0 -> "待审批";
            case 1 -> "已通过";
            case 2 -> "已驳回";
            default -> "未知";
        };
    }

}
