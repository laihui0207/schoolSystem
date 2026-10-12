package com.school.module.warning.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.school.common.exception.BusinessException;
import com.school.common.result.ResultCode;
import com.school.module.config.service.ConfigService;
import com.school.module.exam.entity.Exam;
import com.school.module.exam.entity.ExamSubject;
import com.school.module.exam.mapper.ExamMapper;
import com.school.module.exam.mapper.ExamSubjectMapper;
import com.school.module.notification.dto.NotificationCreateRequest;
import com.school.module.notification.entity.Notification;
import com.school.module.notification.mapper.NotificationMapper;
import com.school.module.notification.service.NotificationService;
import com.school.module.score.entity.Score;
import com.school.module.score.mapper.ScoreMapper;
import com.school.module.student.entity.Student;
import com.school.module.student.mapper.StudentMapper;
import com.school.module.subject.entity.Subject;
import com.school.module.subject.mapper.SubjectMapper;
import com.school.security.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 成绩预警服务实现类
 * <p>
 * 预警两类：单科不及格（低于 score.pass.line）、单科较上次考试大幅下降（跌幅 >= score.warning.threshold）。
 * "上次考试" = 同校、考试日期早于本次、已发布、且年级有交集的最近一场考试。
 *
 * @author School System
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScoreWarningServiceImpl implements ScoreWarningService {

    private final ScoreMapper scoreMapper;
    private final NotificationService notificationService;
    private final StudentMapper studentMapper;
    private final ExamMapper examMapper;
    private final SubjectMapper subjectMapper;
    private final ConfigService configService;
    private final ExamSubjectMapper examSubjectMapper;
    private final NotificationMapper notificationMapper;

    /** 系统配置键：成绩下降预警阈值（分） */
    private static final String KEY_DROP_THRESHOLD = "score.warning.threshold";

    /** 系统配置键：及格分数线 */
    private static final String KEY_PASS_LINE = "score.pass.line";

    /** 配置缺失或非法时的默认值 */
    private static final BigDecimal DEFAULT_DROP_THRESHOLD = BigDecimal.valueOf(20);
    private static final BigDecimal DEFAULT_PASS_LINE = BigDecimal.valueOf(60);

    /** 趋势判定：首尾差在 ±5 分内视为平稳 */
    private static final BigDecimal TREND_STABLE_BAND = BigDecimal.valueOf(5);

    /** 趋势分析取最近几次考试 */
    private static final int TREND_EXAM_COUNT = 5;

    /** 回溯"上次考试"时最多看几场候选（已发布考试总量有限，宁可多取也不能让本年级基线被别的年级挤掉） */
    private static final int PREVIOUS_EXAM_CANDIDATES = 200;

    private static final int EXAM_STATUS_PUBLISHED = 3;
    private static final int SCORE_STATUS_APPROVED = 1;
    private static final int NOTIFICATION_TYPE_WARNING = 2;
    private static final String WARNING_TITLE = "成绩预警通知";

    private static final String TREND_UP = "up";
    private static final String TREND_DOWN = "down";
    private static final String TREND_STABLE = "stable";

    /** 趋势值口径：rate = 各科得分率（%）平均，score = 指定科目原始分 */
    private static final String METRIC_RATE = "rate";
    private static final String METRIC_SCORE = "score";
    private static final BigDecimal PERCENT = BigDecimal.valueOf(100);

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void checkScoreWarnings(Long examId) {
        Exam exam = requirePublishedExam(examId);

        // 查询该考试的所有成绩
        List<Score> currentScores = scoreMapper.selectByExamId(SecurityUtils.currentSchoolIdForQuery(), examId, null);
        if (currentScores.isEmpty()) {
            return;
        }

        BigDecimal dropThreshold = readConfig(KEY_DROP_THRESHOLD, DEFAULT_DROP_THRESHOLD);
        BigDecimal passLine = readConfig(KEY_PASS_LINE, DEFAULT_PASS_LINE);

        // 上次考试成绩索引 (studentId:subjectId) -> 分数，一次性取出，避免按学生逐个查库
        Map<String, BigDecimal> previousScores = loadPreviousScores(exam);

        Set<Long> subjectIds = currentScores.stream().map(Score::getSubjectId).collect(Collectors.toSet());
        Map<Long, String> subjectNames = subjectNamesByIds(subjectIds);

        // 按学生分组，逐科生成预警明细
        Map<Long, List<Score>> studentScoresMap = currentScores.stream()
                .collect(Collectors.groupingBy(Score::getStudentId, LinkedHashMap::new, Collectors.toList()));

        Map<Long, List<String>> warningDetails = new LinkedHashMap<>();
        for (Map.Entry<Long, List<Score>> entry : studentScoresMap.entrySet()) {
            List<String> details = new ArrayList<>();
            for (Score score : entry.getValue()) {
                if (!isValidScore(score)) {
                    continue;
                }
                BigDecimal previous = previousScores.get(scoreKey(score.getStudentId(), score.getSubjectId()));
                String detail = describeSubject(subjectName(subjectNames, score.getSubjectId()),
                        score.getScore(), previous, passLine, dropThreshold);
                if (detail != null) {
                    details.add(detail);
                }
            }
            if (!details.isEmpty()) {
                warningDetails.put(entry.getKey(), details);
            }
        }

        if (warningDetails.isEmpty()) {
            return;
        }

        // 发送预警通知：收件人是 sys_user.id，需经 student.user_id 换算
        Map<Long, Long> userIdByStudentId = userIdsByStudentIds(warningDetails.keySet());
        // ponytail: 同一考试重复触发（发布后管理员再手动点一次）不能让学生再收一遍相同通知
        Set<Long> alreadyWarned = warnedUserIds(exam.getId());
        int sent = 0;
        int skipped = 0;
        for (Map.Entry<Long, List<String>> entry : warningDetails.entrySet()) {
            Long userId = userIdByStudentId.get(entry.getKey());
            if (userId == null) {
                log.warn("学生 {} 未绑定登录账号，跳过预警通知", entry.getKey());
                continue;
            }
            if (alreadyWarned.contains(userId)) {
                skipped++;
                continue;
            }
            sendWarning(exam, userId, entry.getValue());
            sent++;
        }
        log.info("考试 {} 预警检查完成：预警学生 {} 人，已发通知 {} 条，已发过跳过 {} 条",
                examId, warningDetails.size(), sent, skipped);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void checkScoreDropWarning(Long studentId, Long examId, Long subjectId) {
        Exam exam = requirePublishedExam(examId);

        Score current = selectScore(examId, studentId, subjectId);
        if (current == null || !isValidScore(current)) {
            return;
        }

        Exam previousExam = findPreviousExam(exam);
        if (previousExam == null) {
            return;
        }
        Score previous = selectScore(previousExam.getId(), studentId, subjectId);
        if (previous == null || !isValidScore(previous)) {
            return;
        }

        BigDecimal dropThreshold = readConfig(KEY_DROP_THRESHOLD, DEFAULT_DROP_THRESHOLD);
        BigDecimal passLine = readConfig(KEY_PASS_LINE, DEFAULT_PASS_LINE);
        String detail = describeSubject(subjectName(subjectNamesByIds(Set.of(subjectId)), subjectId),
                current.getScore(), previous.getScore(), passLine, dropThreshold);
        if (detail == null) {
            return;
        }

        Long userId = userIdsByStudentIds(List.of(studentId)).get(studentId);
        if (userId == null) {
            log.warn("学生 {} 未绑定登录账号，跳过预警通知", studentId);
            return;
        }
        sendWarning(exam, userId, List.of(detail));
    }

    @Override
    public Map<String, Object> analyzeScoreTrend(Long studentId, Long subjectId) {
        // ponytail: studentId 来自路径参数，学生不比对 currentStudentId 就能遍历全校同学的成绩
        if (SecurityUtils.isStudent() && !Objects.equals(studentId, SecurityUtils.currentStudentId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "学生只能查询本人的成绩趋势");
        }

        List<Score> scores = scoreMapper.selectList(new LambdaQueryWrapper<Score>()
                .eq(Score::getStudentId, studentId)
                .eq(subjectId != null, Score::getSubjectId, subjectId));
        List<Score> validScores = scores.stream().filter(this::isValidScore).collect(Collectors.toList());

        // 学生只看已发布的考试，教师/管理员可看全部
        boolean publishedOnly = SecurityUtils.isStudent();
        Map<Long, Exam> exams = examsByIds(
                validScores.stream().map(Score::getExamId).collect(Collectors.toSet()), publishedOnly);

        // 每场考试一个值：指定科目时为该科原始分，未指定时为各科得分率（%）的平均
        // ponytail: 各科原始分直接相加没有可比性——月考 3 科合计 270 与期中 9 科合计 650 相邻就是一次"暴跌"，
        // 会制造虚假 down/warningLevel；改按 exam_subject.full_score 折算得分率再求平均
        boolean byRate = subjectId == null;
        Map<Long, BigDecimal> valueByExam = byRate
                ? averageRateByExam(validScores, exams.keySet())
                : rawScoreByExam(validScores, exams.keySet());

        Comparator<Exam> byExamDate = Comparator.comparing(Exam::getExamDate,
                Comparator.nullsFirst(Comparator.naturalOrder()));
        List<Exam> ordered = valueByExam.keySet().stream()
                .map(exams::get)
                .sorted(byExamDate.thenComparing(Exam::getId))
                .collect(Collectors.toList());
        List<Exam> recent = ordered.size() > TREND_EXAM_COUNT
                ? ordered.subList(ordered.size() - TREND_EXAM_COUNT, ordered.size())
                : ordered;

        List<BigDecimal> values = recent.stream().map(e -> valueByExam.get(e.getId())).collect(Collectors.toList());
        BigDecimal dropThreshold = readConfig(KEY_DROP_THRESHOLD, DEFAULT_DROP_THRESHOLD);

        Map<String, Object> result = new HashMap<>();
        result.put("studentId", studentId);
        result.put("subjectId", subjectId);
        // 口径说明：rate = 各科得分率（%）平均；score = 指定科目原始分
        result.put("metric", byRate ? METRIC_RATE : METRIC_SCORE);
        result.put("trend", resolveTrend(values));
        result.put("warningLevel", resolveWarningLevel(values, dropThreshold));
        result.put("dropThreshold", dropThreshold);
        result.put("scores", buildTrendPoints(recent, valueByExam));
        return result;
    }

    // ------------------------------------------------------------------
    // 预警规则
    // ------------------------------------------------------------------

    /**
     * 生成单科预警描述；无预警返回 null
     * <p>
     * 示例：「数学 52 分（不及格）」「英语 71 分（较上次下降 23 分）」「物理 48 分（不及格，较上次下降 25 分）」
     */
    private String describeSubject(String subjectName, BigDecimal current, BigDecimal previous,
                                   BigDecimal passLine, BigDecimal dropThreshold) {
        boolean failed = current.compareTo(passLine) < 0;
        BigDecimal drop = previous != null ? previous.subtract(current) : null;
        boolean dropped = drop != null && drop.compareTo(dropThreshold) >= 0;
        if (!failed && !dropped) {
            return null;
        }

        StringBuilder sb = new StringBuilder(subjectName).append(' ').append(plain(current)).append(" 分（");
        if (failed) {
            sb.append("不及格");
        }
        if (dropped) {
            sb.append(failed ? "，" : "").append("较上次下降 ").append(plain(drop)).append(" 分");
        }
        return sb.append('）').toString();
    }

    /**
     * 趋势：按首尾差 ±5 分判定
     */
    private String resolveTrend(List<BigDecimal> values) {
        if (values.size() < 2) {
            return TREND_STABLE;
        }
        BigDecimal diff = values.get(values.size() - 1).subtract(values.get(0));
        if (diff.compareTo(TREND_STABLE_BAND) > 0) {
            return TREND_UP;
        }
        if (diff.compareTo(TREND_STABLE_BAND.negate()) < 0) {
            return TREND_DOWN;
        }
        return TREND_STABLE;
    }

    /**
     * 预警级别：0 正常 / 1 最近一次下降 >= 阈值 / 2 连续两次下降且累计跌幅 >= 阈值
     * <p>
     * ponytail: 原先任意幅度的连续两次下降直接给最高级 2（[90,89,88] 比 [90,60] 还严重），阈值对级别 2 完全不生效；
     * 现在连续两次小幅下降不触发，级别 2 必须累计跌幅达到阈值
     */
    private int resolveWarningLevel(List<BigDecimal> values, BigDecimal dropThreshold) {
        int n = values.size();
        if (n < 2) {
            return 0;
        }
        // 正数表示下降
        BigDecimal lastDrop = values.get(n - 2).subtract(values.get(n - 1));
        if (n >= 3) {
            BigDecimal prevDrop = values.get(n - 3).subtract(values.get(n - 2));
            boolean twoConsecutiveDrops = lastDrop.signum() > 0 && prevDrop.signum() > 0;
            if (twoConsecutiveDrops && lastDrop.add(prevDrop).compareTo(dropThreshold) >= 0) {
                return 2;
            }
        }
        return lastDrop.compareTo(dropThreshold) >= 0 ? 1 : 0;
    }

    private List<Map<String, Object>> buildTrendPoints(List<Exam> exams, Map<Long, BigDecimal> valueByExam) {
        List<Map<String, Object>> points = new ArrayList<>(exams.size());
        for (Exam exam : exams) {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("examId", exam.getId());
            point.put("examName", exam.getExamName());
            point.put("examDate", exam.getExamDate());
            point.put("score", valueByExam.get(exam.getId()));
            points.add(point);
        }
        return points;
    }

    /**
     * 指定科目：每场考试取该科原始分
     */
    private Map<Long, BigDecimal> rawScoreByExam(List<Score> scores, Set<Long> examIds) {
        Map<Long, BigDecimal> valueByExam = new HashMap<>();
        for (Score score : scores) {
            if (examIds.contains(score.getExamId())) {
                valueByExam.merge(score.getExamId(), score.getScore(), BigDecimal::add);
            }
        }
        return valueByExam;
    }

    /**
     * 未指定科目：每场考试取各科得分率（score / exam_subject.full_score × 100）的平均值，保留两位小数
     * <p>
     * 没有满分配置的科目无法折算，跳过；整场都没法折算的考试不进入趋势。
     */
    private Map<Long, BigDecimal> averageRateByExam(List<Score> scores, Set<Long> examIds) {
        Map<String, BigDecimal> fullScores = fullScoresByExamSubject(examIds);
        Map<Long, BigDecimal> rateSum = new HashMap<>();
        Map<Long, Integer> rateCount = new HashMap<>();
        for (Score score : scores) {
            if (!examIds.contains(score.getExamId())) {
                continue;
            }
            BigDecimal fullScore = fullScores.get(examSubjectKey(score.getExamId(), score.getSubjectId()));
            if (fullScore == null || fullScore.signum() <= 0) {
                continue;
            }
            BigDecimal rate = score.getScore().multiply(PERCENT).divide(fullScore, 4, RoundingMode.HALF_UP);
            rateSum.merge(score.getExamId(), rate, BigDecimal::add);
            rateCount.merge(score.getExamId(), 1, Integer::sum);
        }
        Map<Long, BigDecimal> valueByExam = new HashMap<>();
        rateSum.forEach((examId, sum) -> valueByExam.put(examId,
                sum.divide(BigDecimal.valueOf(rateCount.get(examId).longValue()), 2, RoundingMode.HALF_UP)));
        return valueByExam;
    }

    /**
     * (examId:subjectId) -> 满分；exam_subject 属于 exam 模块，这里只读
     */
    private Map<String, BigDecimal> fullScoresByExamSubject(Set<Long> examIds) {
        if (examIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return examSubjectMapper.selectList(new LambdaQueryWrapper<ExamSubject>()
                        .in(ExamSubject::getExamId, examIds)
                        .eq(ExamSubject::getDeleted, 0)).stream()
                .filter(examSubject -> examSubject.getFullScore() != null)
                .collect(Collectors.toMap(
                        examSubject -> examSubjectKey(examSubject.getExamId(), examSubject.getSubjectId()),
                        ExamSubject::getFullScore,
                        (a, b) -> a));
    }

    // ------------------------------------------------------------------
    // 数据读取
    // ------------------------------------------------------------------

    private Exam requireExam(Long examId) {
        Exam exam = examMapper.selectById(examId);
        if (exam == null) {
            throw new BusinessException(ResultCode.EXAM_NOT_FOUND);
        }
        return exam;
    }

    /**
     * 考试必须存在且已发布
     */
    private Exam requirePublishedExam(Long examId) {
        Exam exam = requireExam(examId);
        // ponytail: 录入中/审核中的考试含待审/驳回分数，拿它推预警会把未核实的分数送到学生手里
        if (!Integer.valueOf(EXAM_STATUS_PUBLISHED).equals(exam.getStatus())) {
            throw new BusinessException("仅已发布的考试可做预警检查");
        }
        return exam;
    }

    /**
     * 该考试已经收到过预警通知的用户（type=2, related_id=examId）
     */
    private Set<Long> warnedUserIds(Long examId) {
        return notificationMapper.selectList(new LambdaQueryWrapper<Notification>()
                        .select(Notification::getUserId)
                        .eq(Notification::getType, NOTIFICATION_TYPE_WARNING)
                        .eq(Notification::getRelatedId, examId)).stream()
                .map(Notification::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * 找"上次考试"：同校、考试日期早于本次、已发布、年级有交集的最近一场
     */
    private Exam findPreviousExam(Exam current) {
        if (current.getExamDate() == null) {
            return null;
        }
        // ponytail: 只比对已发布考试——未发布成绩尚未审核，拿它做基线会把错误分数写进学生通知
        // ponytail: 原先 LIMIT 10 后再按年级过滤，多年级学校最近 10 场可能全是别的年级，本年级基线静默丢失；
        // 已发布考试总量有限，放宽候选上限后在内存按年级交集选第一场；同日多场按 id 倒序保证稳定
        List<Exam> candidates = examMapper.selectList(new LambdaQueryWrapper<Exam>()
                .eq(Exam::getSchoolId, current.getSchoolId())
                .lt(Exam::getExamDate, current.getExamDate())
                .eq(Exam::getStatus, EXAM_STATUS_PUBLISHED)
                .ne(Exam::getId, current.getId())
                .orderByDesc(Exam::getExamDate)
                .orderByDesc(Exam::getId)
                .last("LIMIT " + PREVIOUS_EXAM_CANDIDATES));
        // ponytail: 不同年级常在不同日期考试，不看年级交集会拿别的年级的考试当基线，所有学生都查不到上次分数
        Set<String> grades = splitGradeIds(current.getGradeIds());
        return candidates.stream()
                .filter(candidate -> sameGradeScope(grades, splitGradeIds(candidate.getGradeIds())))
                .findFirst()
                .orElse(null);
    }

    /**
     * 年级范围是否匹配：有交集即匹配；grade_ids 为空的候选只匹配同样为空的当前考试
     * <p>
     * ponytail: 原先空 grade_ids 的候选匹配任何年级，会抢占更早的真正同年级考试，而它的成绩里根本没有这些学生
     */
    private static boolean sameGradeScope(Set<String> current, Set<String> candidate) {
        if (current.isEmpty() || candidate.isEmpty()) {
            return current.isEmpty() && candidate.isEmpty();
        }
        return !Collections.disjoint(current, candidate);
    }

    private Map<String, BigDecimal> loadPreviousScores(Exam current) {
        Exam previous = findPreviousExam(current);
        if (previous == null) {
            log.info("考试 {} 未找到同年级的上一场已发布考试，本次只做不及格判定", current.getId());
            return Collections.emptyMap();
        }
        List<Score> previousScores = scoreMapper.selectByExamId(
                SecurityUtils.currentSchoolIdForQuery(), previous.getId(), null);
        Map<String, BigDecimal> index = new HashMap<>();
        for (Score score : previousScores) {
            if (isValidScore(score)) {
                index.put(scoreKey(score.getStudentId(), score.getSubjectId()), score.getScore());
            }
        }
        return index;
    }

    private Score selectScore(Long examId, Long studentId, Long subjectId) {
        return scoreMapper.selectOne(new LambdaQueryWrapper<Score>()
                .eq(Score::getExamId, examId)
                .eq(Score::getStudentId, studentId)
                .eq(Score::getSubjectId, subjectId)
                .last("LIMIT 1"));
    }

    private Map<Long, Exam> examsByIds(Set<Long> examIds, boolean publishedOnly) {
        if (examIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return examMapper.selectBatchIds(examIds).stream()
                .filter(exam -> !publishedOnly || Integer.valueOf(EXAM_STATUS_PUBLISHED).equals(exam.getStatus()))
                .collect(Collectors.toMap(Exam::getId, exam -> exam, (a, b) -> a));
    }

    private Map<Long, String> subjectNamesByIds(Set<Long> subjectIds) {
        if (subjectIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return subjectMapper.selectBatchIds(subjectIds).stream()
                .collect(Collectors.toMap(Subject::getId, Subject::getSubjectName, (a, b) -> a));
    }

    /**
     * student.id -> sys_user.id；未绑定账号的学生不在结果里
     */
    private Map<Long, Long> userIdsByStudentIds(Collection<Long> studentIds) {
        if (studentIds.isEmpty()) {
            return Collections.emptyMap();
        }
        // ponytail: notification.user_id 是 sys_user.id 而非 student.id，直接拿 Score.studentId 发通知会发给无关账号
        return studentMapper.selectBatchIds(studentIds).stream()
                .filter(student -> student.getUserId() != null)
                .collect(Collectors.toMap(Student::getId, Student::getUserId, (a, b) -> a));
    }

    /**
     * 读取数值型系统配置，缺失或解析失败回退默认值
     */
    private BigDecimal readConfig(String key, BigDecimal defaultValue) {
        // ponytail: 阈值由 sys_config 维护（V8 已有 score.warning.threshold / score.pass.line），不再写死常量
        try {
            String value = configService.getConfigByKey(key);
            return StringUtils.hasText(value) ? new BigDecimal(value.trim()) : defaultValue;
        } catch (Exception e) {
            log.warn("读取配置 {} 失败，使用默认值 {}: {}", key, defaultValue, e.getMessage());
            return defaultValue;
        }
    }

    // ------------------------------------------------------------------
    // 通知
    // ------------------------------------------------------------------

    private void sendWarning(Exam exam, Long userId, List<String> details) {
        NotificationCreateRequest request = new NotificationCreateRequest();
        request.setUserId(userId);
        request.setTitle(WARNING_TITLE);
        request.setContent(String.format("%s：%s。请及时关注学习情况。", exam.getExamName(), String.join("；", details)));
        request.setType(NOTIFICATION_TYPE_WARNING);
        request.setRelatedId(exam.getId());
        notificationService.createNotification(request);
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private boolean isValidScore(Score score) {
        // ponytail: 待审核(0)/已驳回(2) 的分数不能进预警与趋势，否则被驳回的错误分数会以"XX 52 分（不及格）"推给学生
        return score.getScore() != null
                && !Integer.valueOf(1).equals(score.getIsAbsent())
                && Integer.valueOf(SCORE_STATUS_APPROVED).equals(score.getStatus());
    }

    private static String scoreKey(Long studentId, Long subjectId) {
        return studentId + ":" + subjectId;
    }

    private static String examSubjectKey(Long examId, Long subjectId) {
        return examId + ":" + subjectId;
    }

    private static String subjectName(Map<Long, String> names, Long subjectId) {
        return names.getOrDefault(subjectId, "科目" + subjectId);
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static Set<String> splitGradeIds(String gradeIds) {
        if (!StringUtils.hasText(gradeIds)) {
            return Collections.emptySet();
        }
        // ponytail: 库里 grade_ids 既有 "1,2" 也有 "[1,2]"（列注释/seed 用 JSON 数组），与 ExamServiceImpl.parseGradeIds 同步去括号
        return Arrays.stream(gradeIds.replaceAll("[\\[\\]\\s]", "").split(","))
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
    }

}
