package com.school.module.stat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.school.common.exception.BusinessException;
import com.school.common.result.ResultCode;
import com.school.security.service.TeacherScopeService;
import com.school.module.config.service.ScoreRuleService;
import com.school.module.class_module.entity.SchoolClass;
import com.school.module.class_module.mapper.ClassMapper;
import com.school.module.exam.entity.Exam;
import com.school.module.exam.entity.ExamSubject;
import com.school.module.exam.mapper.ExamMapper;
import com.school.module.exam.mapper.ExamSubjectMapper;
import com.school.module.grade.entity.Grade;
import com.school.module.grade.mapper.GradeMapper;
import com.school.module.score.entity.Score;
import com.school.module.score.mapper.ScoreMapper;
import com.school.module.school.entity.School;
import com.school.module.school.mapper.SchoolMapper;
import com.school.module.stat.dto.*;
import com.school.module.student.entity.Student;
import com.school.module.student.mapper.StudentMapper;
import com.school.module.subject.entity.Subject;
import com.school.module.subject.mapper.SubjectMapper;
import com.school.module.teacher.entity.TeacherClass;
import com.school.module.term.entity.AcademicTerm;
import com.school.module.term.mapper.AcademicTermMapper;
import com.school.security.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 成绩统计服务实现类
 *
 * @author School System
 */
@Service
@RequiredArgsConstructor
public class StatServiceImpl implements StatService {

    // ponytail: 及格/优秀线不再写死 60/85，改为按科目配置折算（见 subjectLines）；这里只保留兜底满分
    private static final BigDecimal DEFAULT_FULL_SCORE = BigDecimal.valueOf(100);
    // ponytail: K1 —— 比例改从 ScoreRuleService（sys_config score.pass.line / score.excellent.line）读取；
    // 这两个常量只在配置服务返回空或非正数时兜底，保证统计不会因配置损坏而抛错
    private static final BigDecimal DEFAULT_PASS_RATIO = new BigDecimal("0.60");
    private static final BigDecimal DEFAULT_EXCELLENT_RATIO = new BigDecimal("0.85");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int TOP_STUDENT_LIMIT = 10;
    // ponytail: 契约 S —— 学生只看已发布考试，与 ExamServiceImpl.STATUS_PUBLISHED 同值
    private static final int EXAM_STATUS_PUBLISHED = 3;
    // ponytail: 停用（status=0）的学校在业务上等于已移除（见 api/school.ts），不参与区域对比也不计入区域学校数
    private static final int SCHOOL_STATUS_ENABLED = 1;

    private final ScoreMapper scoreMapper;
    private final ClassMapper classMapper;
    private final SubjectMapper subjectMapper;
    private final StudentMapper studentMapper;
    private final ExamMapper examMapper;
    private final ExamSubjectMapper examSubjectMapper;
    private final GradeMapper gradeMapper;
    private final SchoolMapper schoolMapper;
    private final AcademicTermMapper academicTermMapper;
    private final TeacherScopeService teacherScopeService;
    private final ScoreRuleService scoreRuleService;

    @Override
    public PersonalStatResponse getPersonalStat(Long examId, Long studentId) {
        // ponytail: 学生只能查自己的个人统计（IDOR），与 getStudentTrend 的守卫一致
        assertStudentSelfOnly(studentId, "无权查看其他学生的成绩统计");
        Exam exam = examMapper.selectById(examId);
        // ponytail: 契约 S —— 学生只能看已发布考试（exam.status==3）的成绩；发布前先拒绝，不读成绩
        if (SecurityUtils.isStudent() && !isPublished(exam)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "该考试成绩尚未发布");
        }
        // 整场考试的成绩一次拉取：既取该生各科成绩，也用于现算总分名次
        List<Score> examScores = scoreMapper.selectByExamId(SecurityUtils.currentSchoolIdForQuery(), examId, null);
        List<Score> scores = examScores.stream()
                .filter(s -> s.getStudentId().equals(studentId))
                .collect(Collectors.toList());

        if (scores.isEmpty()) {
            throw new BusinessException("未找到学生成绩");
        }

        // ponytail: is_absent 在 DDL 里可空（SMALLINT DEFAULT 0），原 == 0 直接拆箱会 NPE；复用空安全的 validScores
        List<Score> valid = validScores(scores);

        // 计算总分
        BigDecimal totalScore = valid.stream()
                .map(Score::getScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 计算平均分
        long validCount = valid.size();

        BigDecimal averageScore = validCount > 0 ?
                totalScore.divide(BigDecimal.valueOf(validCount), 2, RoundingMode.HALF_UP) :
                BigDecimal.ZERO;

        // ponytail: 骨架字段补齐 —— 学生姓名/学号、考试名、各科名称与满分、总满分
        Student student = studentMapper.selectById(studentId);
        Set<Long> subjectIds = ids(scores, Score::getSubjectId);
        Map<Long, Subject> subjects = byId(subjectMapper, subjectIds, Subject::getId);
        Map<Long, String> subjectNames = names(subjects, subjectIds, Subject::getSubjectName, "科目");
        Map<Long, ScoreLine> lines = subjectLines(examId, subjectIds, subjects);

        // 构建科目成绩列表
        List<PersonalStatResponse.SubjectScore> subjectScores = scores.stream()
                .map(score -> PersonalStatResponse.SubjectScore.builder()
                        .subjectId(score.getSubjectId())
                        .subjectName(subjectNames.get(score.getSubjectId()))
                        .score(score.getScore())
                        .fullScore(fullScoreOf(lines, score.getSubjectId()))
                        .rankInClass(score.getRankInClass())
                        .rankInGrade(score.getRankInGrade())
                        .isAbsent(absent(score))
                        .build())
                .collect(Collectors.toList());

        // ponytail: 契约 R —— 顶层总分名次优先读 Score.totalRankInClass/totalRankInGrade（同一学生各行相同），
        // 历史数据未重算（为空）时再按该生总分在同班/同年级学生中现算；行上 rank_in_* 是单科名次，不能用
        TotalRank totalRank = totalRankOf(scores, studentId, validCount > 0, () -> examScores);

        return PersonalStatResponse.builder()
                .studentId(studentId)
                .studentName(student == null ? null : student.getName())
                .studentNo(student == null ? null : student.getStudentNo())
                .examId(examId)
                .examName(exam == null ? null : exam.getExamName())
                .totalScore(totalScore)
                .totalFullScore(totalLine(scores, lines).fullScore())
                .averageScore(averageScore)
                .rankInClass(totalRank.rankInClass())
                .rankInGrade(totalRank.rankInGrade())
                .subjectScores(subjectScores)
                .build();
    }

    @Override
    public StudentTrendResponse getStudentTrend(Long studentId, Long subjectId) {
        return getStudentTrend(studentId, subjectId, null);
    }

    @Override
    public StudentTrendResponse getStudentTrend(Long studentId, Long subjectId, Long termId) {
        // 学生角色只能查自己的趋势
        assertStudentSelfOnly(studentId, "无权查看其他学生的成绩趋势");
        // 按学生直接查成绩（原实现 selectByExamId(null, null) 生成 exam_id = NULL，永远为空）
        List<Score> allScores = scoreMapper.selectList(new LambdaQueryWrapper<Score>()
                .eq(Score::getStudentId, studentId)
                .eq(subjectId != null, Score::getSubjectId, subjectId));

        Map<Long, Exam> exams = byId(examMapper, ids(allScores, Score::getExamId), Exam::getId);
        // ponytail: 契约 S —— 学生只看已发布考试（exam.status==3），与 ScoreMapper.xml / listScores 的 publishedOnly 口径一致
        if (SecurityUtils.isStudent()) {
            allScores = allScores.stream()
                    .filter(s -> isPublished(exams.get(s.getExamId())))
                    .collect(Collectors.toList());
        }
        if (termId != null) {
            allScores = allScores.stream()
                    .filter(s -> {
                        Exam exam = exams.get(s.getExamId());
                        return exam != null && termId.equals(exam.getTermId());
                    })
                    .collect(Collectors.toList());
        }

        Student student = studentMapper.selectById(studentId);
        String studentName = student == null ? null : student.getName();

        if (allScores.isEmpty()) {
            return StudentTrendResponse.builder()
                    .studentId(studentId)
                    .studentName(studentName)
                    .examTrends(new ArrayList<>())
                    .subjectRadars(new ArrayList<>())
                    .build();
        }

        Set<Long> subjectIds = ids(allScores, Score::getSubjectId);
        Map<Long, Subject> subjects = byId(subjectMapper, subjectIds, Subject::getId);
        Map<Long, String> subjectNames = names(subjects, subjectIds, Subject::getSubjectName, "科目");

        // 按考试ID分组
        Map<Long, List<Score>> examScoresMap = allScores.stream()
                .collect(Collectors.groupingBy(Score::getExamId));

        // 构建考试趋势数据
        Long schoolId = SecurityUtils.currentSchoolIdForQuery();
        List<StudentTrendResponse.ExamTrend> examTrends = new ArrayList<>();
        for (Map.Entry<Long, List<Score>> entry : examScoresMap.entrySet()) {
            Long examId = entry.getKey();
            List<Score> validScores = validScores(entry.getValue());
            Summary summary = summarize(scoreValues(validScores));
            Exam exam = exams.get(examId);
            Score firstScore = entry.getValue().get(0);
            // ponytail: 契约 R —— 总分趋势（subjectId 为空）的名次读 Score.totalRank*，为空（历史数据未重算）则按全场成绩现算；
            // 原来取第一条科目行的 rank_in_* 是单科名次，前端画出的「班级名次」是某个随机科目的
            TotalRank rank = subjectId == null
                    ? totalRankOf(entry.getValue(), studentId, !validScores.isEmpty(),
                            () -> scoreMapper.selectByExamId(schoolId, examId, null))
                    : new TotalRank(firstScore.getRankInClass(), firstScore.getRankInGrade());

            examTrends.add(StudentTrendResponse.ExamTrend.builder()
                    .examId(examId)
                    .examName(exam == null || exam.getExamName() == null ? "考试" + examId : exam.getExamName())
                    .examDate(exam == null ? null : exam.getExamDate())
                    .totalScore(summary.total())
                    .averageScore(summary.average())
                    .rankInClass(rank.rankInClass())
                    .rankInGrade(rank.rankInGrade())
                    .subjectScores(subjectScoreMap(validScores, subjectNames))
                    .build());
        }

        // 趋势按考试日期升序，无日期的排最后
        Comparator<StudentTrendResponse.ExamTrend> byExamDate = Comparator.comparing(
                StudentTrendResponse.ExamTrend::getExamDate,
                Comparator.nullsLast(Comparator.<LocalDate>naturalOrder()));
        examTrends.sort(byExamDate.thenComparing(StudentTrendResponse.ExamTrend::getExamId));

        // 构建雷达图数据（最近一次考试）
        Long latestExamId = examTrends.get(examTrends.size() - 1).getExamId();
        List<Score> latestScores = validScores(examScoresMap.get(latestExamId));
        // ponytail: 满分取 exam_subject.full_score（按最近一次考试），兜底 subject.full_score / 100；得分率保留 1 位小数
        Map<Long, ScoreLine> latestLines = subjectLines(latestExamId, ids(latestScores, Score::getSubjectId), subjects);
        List<StudentTrendResponse.SubjectRadar> subjectRadars = latestScores.stream()
                .map(score -> {
                    BigDecimal fullScore = fullScoreOf(latestLines, score.getSubjectId());
                    return StudentTrendResponse.SubjectRadar.builder()
                            .subjectId(score.getSubjectId())
                            .subjectName(subjectNames.get(score.getSubjectId()))
                            .score(score.getScore())
                            .fullScore(fullScore)
                            .percentage(percentage(score.getScore(), fullScore))
                            .build();
                })
                .collect(Collectors.toList());

        return StudentTrendResponse.builder()
                .studentId(studentId)
                .studentName(studentName)
                .examTrends(examTrends)
                .subjectRadars(subjectRadars)
                .build();
    }

    @Override
    public ClassStatResponse getClassStat(Long examId, Long classId) {
        // 教师权限校验
        checkTeacherScope(classId, null);

        // 查询该班级该次考试的所有成绩
        List<Score> scores = scoreMapper.selectByExamId(SecurityUtils.currentSchoolIdForQuery(), examId, classId);

        if (scores.isEmpty()) {
            throw new BusinessException("未找到班级成绩");
        }

        // 计算基础统计数据
        int totalStudents = (int) scores.stream()
                .map(Score::getStudentId)
                .distinct()
                .count();

        // 过滤有效成绩
        List<Score> validScores = validScores(scores);

        // 计算总分（按学生分组求和）
        Map<Long, BigDecimal> studentTotalScores = validScores.stream()
                .collect(Collectors.groupingBy(
                        Score::getStudentId,
                        Collectors.reducing(BigDecimal.ZERO, Score::getScore, BigDecimal::add)
                ));

        // ponytail: 分母改为「至少有一科有效成绩的学生数」= studentTotalScores.size()，与 getGradeStat 的 summarize 口径一致；
        // 原来 totalStudents − 任一科缺考人数 会把部分缺考学生的总分留在分子却从分母剔除，均分虚高、及格率可超 100%
        int actualStudents = studentTotalScores.size();
        int absentStudents = totalStudents - actualStudents;

        if (validScores.isEmpty()) {
            return ClassStatResponse.builder()
                    .classId(classId)
                    .examId(examId)
                    .totalStudents(totalStudents)
                    .actualStudents(actualStudents)
                    .absentStudents(absentStudents)
                    .totalScore(BigDecimal.ZERO)
                    .averageScore(BigDecimal.ZERO)
                    .maxScore(BigDecimal.ZERO)
                    .minScore(BigDecimal.ZERO)
                    .passCount(0)
                    .passRate(BigDecimal.ZERO)
                    .excellentCount(0)
                    .excellentRate(BigDecimal.ZERO)
                    .subjectStats(new ArrayList<>())
                    .scoreRanges(new ArrayList<>())
                    .topStudents(new ArrayList<>())
                    .build();
        }

        BigDecimal totalScore = studentTotalScores.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal averageScore = actualStudents > 0 ?
                totalScore.divide(BigDecimal.valueOf(actualStudents), 2, RoundingMode.HALF_UP) :
                BigDecimal.ZERO;

        // 最高分和最低分
        BigDecimal maxScore = studentTotalScores.values().stream()
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        BigDecimal minScore = studentTotalScores.values().stream()
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        // ponytail: 及格/优秀线按科目配置折算：总分口径 = Σ各科及格线 / Σ各科满分×85%，不再按百分制 60/85
        Set<Long> subjectIds = ids(scores, Score::getSubjectId);
        Map<Long, Subject> subjects = byId(subjectMapper, subjectIds, Subject::getId);
        Map<Long, String> subjectNames = names(subjects, subjectIds, Subject::getSubjectName, "科目");
        Map<Long, ScoreLine> lines = subjectLines(examId, subjectIds, subjects);
        ScoreLine totalLine = totalLine(scores, lines);

        // 计算及格率和优秀率
        long passCount = studentTotalScores.values().stream()
                .filter(score -> score.compareTo(totalLine.passLine()) >= 0)
                .count();

        long excellentCount = studentTotalScores.values().stream()
                .filter(score -> score.compareTo(totalLine.excellentLine()) >= 0)
                .count();

        BigDecimal passRate = rate(passCount, actualStudents);
        BigDecimal excellentRate = rate(excellentCount, actualStudents);

        // 计算分数段分布（按总分/总满分的得分率分段）
        List<ClassStatResponse.ScoreRange> scoreRanges = calculateScoreRanges(
                new ArrayList<>(studentTotalScores.values()), totalLine.fullScore()
        );

        // 各科目统计（每科用各自科目的线）
        List<ClassStatResponse.SubjectStat> subjectStats = groupValues(validScores, Score::getSubjectId).entrySet().stream()
                .map(entry -> {
                    Summary summary = summarize(entry.getValue(), lines.get(entry.getKey()));
                    return ClassStatResponse.SubjectStat.builder()
                            .subjectId(entry.getKey())
                            .subjectName(subjectNames.get(entry.getKey()))
                            .averageScore(summary.average())
                            .maxScore(summary.max())
                            .minScore(summary.min())
                            .passRate(summary.passRate())
                            .excellentRate(summary.excellentRate())
                            .build();
                })
                .collect(Collectors.toList());

        // 总分排名前列的学生
        List<ClassStatResponse.TopStudent> topStudents = topStudents(studentTotalScores, validScores, subjectNames);

        return ClassStatResponse.builder()
                .classId(classId)
                .examId(examId)
                .totalStudents(totalStudents)
                .actualStudents(actualStudents)
                .absentStudents(absentStudents)
                .totalScore(totalScore)
                .averageScore(averageScore)
                .maxScore(maxScore)
                .minScore(minScore)
                .passCount((int) passCount)
                .passRate(passRate)
                .excellentCount((int) excellentCount)
                .excellentRate(excellentRate)
                .subjectStats(subjectStats)
                .scoreRanges(scoreRanges)
                .topStudents(topStudents)
                .build();
    }

    @Override
    public GradeStatResponse getGradeStat(Long examId, Long gradeId) {
        Long schoolId = SecurityUtils.currentSchoolIdForQuery();
        // ponytail: 教师不再整体拒绝（菜单对教师可见），改为把结果限定在其可访问班级（任教 ∪ 班主任）内
        Set<Long> teacherClassIds = teacherVisibleClassIds(null);
        // ponytail: gradeId 非空时按 class.grade_id 反查班级集合过滤成绩，为空时保持全校总览
        Set<Long> gradeClassIds = gradeClassIds(gradeId, schoolId);

        Exam exam = examMapper.selectById(examId);
        // ponytail: examName 原来写死 "考试"+id 占位，改为取真名
        String examName = exam == null || exam.getExamName() == null ? "考试" + examId : exam.getExamName();

        List<Score> allScores = limitToClasses(
                limitToClasses(scoreMapper.selectByExamId(schoolId, examId, null), gradeClassIds),
                teacherClassIds);

        Map<Long, SchoolClass> classes = byId(classMapper, ids(allScores, Score::getClassId), SchoolClass::getId);
        Set<Long> gradeIdsToResolve = new HashSet<>(gradeIds(classes));
        if (gradeId != null) {
            gradeIdsToResolve.add(gradeId);
        }
        Map<Long, String> gradeNames = names(gradeMapper, gradeIdsToResolve, Grade::getId, Grade::getGradeName, "年级");
        String gradeName = gradeId == null ? null : gradeNames.getOrDefault(gradeId, "年级" + gradeId);

        if (allScores.isEmpty()) {
            return GradeStatResponse.builder()
                    .gradeId(gradeId)
                    .gradeName(gradeName)
                    .examId(examId)
                    .examName(examName)
                    .totalStudents(0)
                    .averageScore(BigDecimal.ZERO)
                    .maxScore(BigDecimal.ZERO)
                    .minScore(BigDecimal.ZERO)
                    .passRate(BigDecimal.ZERO)
                    .excellentRate(BigDecimal.ZERO)
                    .classComparisons(new ArrayList<>())
                    .gradeStats(new ArrayList<>())
                    .subjectStats(new ArrayList<>())
                    .build();
        }

        List<Score> validScores = validScores(allScores);
        Set<Long> subjectIds = ids(allScores, Score::getSubjectId);
        Map<Long, Subject> subjects = byId(subjectMapper, subjectIds, Subject::getId);
        Map<Long, String> subjectNames = names(subjects, subjectIds, Subject::getSubjectName, "科目");
        Map<Long, ScoreLine> lines = subjectLines(examId, subjectIds, subjects);

        // 按班级分组统计（学生总分）
        Map<Long, List<Score>> classScoresMap = allScores.stream()
                .collect(Collectors.groupingBy(Score::getClassId));

        List<GradeStatResponse.ClassComparison> classComparisons = new ArrayList<>();
        for (Map.Entry<Long, List<Score>> entry : classScoresMap.entrySet()) {
            Long classId = entry.getKey();
            // 每个班按自己实际考的科目集合折算总分线，避免跨年级科目数不同时互相拖累
            ScoreLine classLine = totalLine(entry.getValue(), lines);
            Summary summary = summarize(new ArrayList<>(studentTotals(entry.getValue()).values()), classLine);

            classComparisons.add(GradeStatResponse.ClassComparison.builder()
                    .classId(classId)
                    .className(className(classes, classId))
                    .averageScore(summary.average())
                    .maxScore(summary.max())
                    .minScore(summary.min())
                    .passRate(summary.passRate())
                    .excellentRate(summary.excellentRate())
                    .build());
        }

        // 按平均分排序并设置排名
        classComparisons.sort((c1, c2) -> c2.getAverageScore().compareTo(c1.getAverageScore()));
        for (int i = 0; i < classComparisons.size(); i++) {
            classComparisons.get(i).setRank(i + 1);
        }

        // 年级整体数据
        Map<Long, BigDecimal> allStudentScores = studentTotals(allScores);
        Summary gradeSummary = summarize(new ArrayList<>(allStudentScores.values()), totalLine(allScores, lines));

        // 各年级对比：学生 -> 班级 -> 年级
        Map<Long, Long> studentClass = allScores.stream()
                .collect(Collectors.toMap(Score::getStudentId, Score::getClassId, (a, b) -> a));
        Map<Long, List<BigDecimal>> gradeTotals = new TreeMap<>();
        allStudentScores.forEach((studentId, total) -> {
            Long studentGradeId = gradeIdOf(classes, studentClass.get(studentId));
            if (studentGradeId != null) {
                gradeTotals.computeIfAbsent(studentGradeId, k -> new ArrayList<>()).add(total);
            }
        });
        List<GradeStatResponse.GradeStat> gradeStats = gradeTotals.entrySet().stream()
                .map(entry -> GradeStatResponse.GradeStat.builder()
                        .gradeId(entry.getKey())
                        .gradeName(gradeNames.get(entry.getKey()))
                        .averageScore(summarize(entry.getValue()).average())
                        .build())
                .collect(Collectors.toList());

        // 各科目统计
        List<GradeStatResponse.SubjectStat> subjectStats = groupValues(validScores, Score::getSubjectId).entrySet().stream()
                .map(entry -> {
                    Summary summary = summarize(entry.getValue(), lines.get(entry.getKey()));
                    return GradeStatResponse.SubjectStat.builder()
                            .subjectId(entry.getKey())
                            .subjectName(subjectNames.get(entry.getKey()))
                            .averageScore(summary.average())
                            .maxScore(summary.max())
                            .minScore(summary.min())
                            .build();
                })
                .collect(Collectors.toList());

        return GradeStatResponse.builder()
                .gradeId(gradeId)
                .gradeName(gradeName)
                .examId(examId)
                .examName(examName)
                .totalStudents(gradeSummary.count())
                .averageScore(gradeSummary.average())
                .maxScore(gradeSummary.max())
                .minScore(gradeSummary.min())
                .passRate(gradeSummary.passRate())
                .excellentRate(gradeSummary.excellentRate())
                .classComparisons(classComparisons)
                .gradeStats(gradeStats)
                .subjectStats(subjectStats)
                .build();
    }

    @Override
    public SubjectStatResponse getSubjectStat(Long examId, Long subjectId) {
        // ponytail: 教师按「任教该科目的班级 ∪ 班主任班级」过滤而不是整体拒绝；classAverages 只含其可见班级
        Set<Long> teacherClassIds = teacherVisibleClassIds(subjectId);
        // ponytail: 原来用 currentSchoolId() 对超管直接抛错，改为 currentSchoolIdForQuery()（超管 null = 不限校）
        List<Score> scores = limitToClasses(
                scoreMapper.selectByExamId(SecurityUtils.currentSchoolIdForQuery(), examId, null, subjectId),
                teacherClassIds);
        List<Score> validScores = validScores(scores);
        List<BigDecimal> values = scoreValues(validScores);

        // ponytail: 单科的及格/优秀线取该科目配置（exam_subject.full_score 优先），不再写死 60/85
        Subject subject = subjectMapper.selectById(subjectId);
        Map<Long, Subject> subjects = subject == null
                ? Collections.emptyMap()
                : Collections.singletonMap(subjectId, subject);
        ScoreLine line = subjectLines(examId, Collections.singleton(subjectId), subjects).get(subjectId);
        Summary summary = summarize(values, line);

        Map<Long, SchoolClass> classes = byId(classMapper, ids(validScores, Score::getClassId), SchoolClass::getId);
        Map<Long, String> gradeNames = names(gradeMapper, gradeIds(classes), Grade::getId, Grade::getGradeName, "年级");

        // 各班级平均分
        List<SubjectStatResponse.ClassAverage> classAverages = groupValues(validScores, Score::getClassId).entrySet().stream()
                .map(entry -> SubjectStatResponse.ClassAverage.builder()
                        .classId(entry.getKey())
                        .className(className(classes, entry.getKey()))
                        .averageScore(summarize(entry.getValue()).average())
                        .build())
                .collect(Collectors.toList());

        // 各年级箱线图五数
        List<SubjectStatResponse.GradeBox> gradeBoxes = groupValues(validScores, s -> gradeIdOf(classes, s.getClassId()))
                .entrySet().stream()
                .map(entry -> {
                    List<BigDecimal> sorted = entry.getValue().stream().sorted().collect(Collectors.toList());
                    return SubjectStatResponse.GradeBox.builder()
                            .gradeId(entry.getKey())
                            .gradeName(gradeNames.get(entry.getKey()))
                            .min(sorted.get(0))
                            .q1(percentile(sorted, 0.25))
                            .median(percentile(sorted, 0.5))
                            .q3(percentile(sorted, 0.75))
                            .max(sorted.get(sorted.size() - 1))
                            .build();
                })
                .collect(Collectors.toList());

        return SubjectStatResponse.builder()
                .subjectId(subjectId)
                .subjectName(subject == null || subject.getSubjectName() == null ? "科目" + subjectId : subject.getSubjectName())
                .examId(examId)
                .totalStudents((int) scores.stream().map(Score::getStudentId).distinct().count())
                .actualStudents(summary.count())
                .averageScore(summary.average())
                .maxScore(summary.max())
                .minScore(summary.min())
                .passRate(summary.passRate())
                .excellentRate(summary.excellentRate())
                .standardDeviation(standardDeviation(values))
                .classAverages(classAverages)
                .gradeBoxes(gradeBoxes)
                .scoreRanges(calculateScoreRanges(values, line == null ? null : line.fullScore()))
                .build();
    }

    // ---------------------------------------------------------------- 区域（跨校）对比

    @Override
    public RegionStatResponse getRegionStat(RegionStatQuery query) {
        // ponytail: Controller 已 @PreAuthorize 超管，这里再拦一次，防止被 AI 工具或其他 Service 直接调用时绕过
        assertSuperAdmin("只有超级管理员可以查看区域对比");
        validateRegionQuery(query);
        String regionCode = query.getRegionCode().trim();
        List<School> schools = regionSchools(regionCode);
        Set<Long> schoolIds = schools.stream().map(School::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Exam> examBySchool = schoolIds.isEmpty()
                ? Collections.emptyMap()
                : latestPublishedExamBySchool(schoolIds, query);

        // ponytail: 超管下 currentSchoolIdForQuery() 为 null、租户插件也跳过，成绩必须按学校显式过滤；每校一次 selectByExamId，学校数有限
        Map<Long, List<Score>> scoresBySchool = new LinkedHashMap<>();
        for (School school : schools) {
            Exam exam = examBySchool.get(school.getId());
            scoresBySchool.put(school.getId(), exam == null
                    ? Collections.<Score>emptyList()
                    : scoreMapper.selectByExamId(school.getId(), exam.getId(), null));
        }
        Set<Long> allSubjectIds = scoresBySchool.values().stream()
                .flatMap(List::stream)
                .map(Score::getSubjectId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Subject> subjects = byId(subjectMapper, allSubjectIds, Subject::getId);

        List<RegionStatResponse.SchoolStat> ranked = new ArrayList<>();
        List<RegionStatResponse.SchoolStat> unranked = new ArrayList<>();
        Map<String, RegionStatResponse.SubjectCompare> subjectCompares = new LinkedHashMap<>();
        for (School school : schools) {
            Exam exam = examBySchool.get(school.getId());
            List<Score> scores = scoresBySchool.get(school.getId());
            Summary summary = exam == null
                    ? summarize(Collections.<BigDecimal>emptyList())
                    : schoolSummary(exam, scores, subjects);
            RegionStatResponse.SchoolStat stat = schoolStat(school, exam, summary);
            // ponytail: 没有该学期该类型已发布考试、或一条有效成绩都没有的学校仍返回（前端显示「—」），但不参与排名和科目对比
            if (summary.count() == 0) {
                unranked.add(stat);
                continue;
            }
            ranked.add(stat);
            collectSubjectAverages(subjectCompares, school.getId(), validScores(scores), subjects);
        }
        assignSchoolRanks(ranked);

        List<RegionStatResponse.SchoolStat> all = new ArrayList<>(ranked);
        all.addAll(unranked);
        return RegionStatResponse.builder()
                .regionCode(regionCode)
                .examType(query.getExamType())
                .year(query.getYear().trim())
                .term(query.getTerm())
                .schools(all)
                .subjects(new ArrayList<>(subjectCompares.values()))
                .build();
    }

    @Override
    public List<RegionOptionResponse> listRegions() {
        assertSuperAdmin("只有超级管理员可以查看区域列表");
        List<School> schools = schoolMapper.selectList(new LambdaQueryWrapper<School>()
                .isNotNull(School::getRegionCode)
                .ne(School::getRegionCode, "")
                .eq(School::getStatus, SCHOOL_STATUS_ENABLED));
        // school 表很小，去重计数在内存做，省一段 resultType="map" 的手写 SQL（PG 驼峰别名坑）
        Map<String, Integer> counts = new TreeMap<>();
        for (School school : schools) {
            String code = school.getRegionCode() == null ? "" : school.getRegionCode().trim();
            if (!code.isEmpty()) {
                counts.merge(code, 1, Integer::sum);
            }
        }
        return counts.entrySet().stream()
                .map(entry -> RegionOptionResponse.builder()
                        .regionCode(entry.getKey())
                        .schoolCount(entry.getValue())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Controller 侧有 @Valid，这里再查一遍是为了直接调用 Service 的路径（AI 工具、其他 Service）
     */
    private void validateRegionQuery(RegionStatQuery query) {
        if (query == null
                || query.getRegionCode() == null || query.getRegionCode().trim().isEmpty()
                || query.getYear() == null || query.getYear().trim().isEmpty()
                || query.getTerm() == null || query.getExamType() == null) {
            throw new BusinessException("区域编码、学年、学期、考试类型均不能为空");
        }
    }

    /**
     * 区域内启用状态的学校，按 ID 升序
     */
    private List<School> regionSchools(String regionCode) {
        return schoolMapper.selectList(new LambdaQueryWrapper<School>()
                .eq(School::getRegionCode, regionCode)
                .eq(School::getStatus, SCHOOL_STATUS_ENABLED)
                .orderByAsc(School::getId));
    }

    /**
     * 每校在 (year, term, examType) 下 exam_date 最新的一场已发布考试
     */
    private Map<Long, Exam> latestPublishedExamBySchool(Set<Long> schoolIds, RegionStatQuery query) {
        // ponytail: 考试按学校建、term_id 跨校不同，没有统一考试 ID；先用各校 academic_term 的 (year, term) 对齐出 term_id 集合
        List<AcademicTerm> terms = academicTermMapper.selectList(new LambdaQueryWrapper<AcademicTerm>()
                .in(AcademicTerm::getSchoolId, schoolIds)
                .eq(AcademicTerm::getYear, query.getYear().trim())
                .eq(AcademicTerm::getTerm, query.getTerm()));
        Set<Long> termIds = terms.stream().map(AcademicTerm::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (termIds.isEmpty()) {
            // ponytail: MyBatis-Plus 对空集合 in() 会生成非法 SQL，必须先短路
            return Collections.emptyMap();
        }
        List<Exam> exams = examMapper.selectList(new LambdaQueryWrapper<Exam>()
                .in(Exam::getSchoolId, schoolIds)
                .in(Exam::getTermId, termIds)
                .eq(Exam::getExamType, query.getExamType())
                .eq(Exam::getStatus, EXAM_STATUS_PUBLISHED));
        Map<Long, Exam> latest = new HashMap<>();
        for (Exam exam : exams) {
            if (exam.getSchoolId() == null) {
                continue;
            }
            Exam current = latest.get(exam.getSchoolId());
            if (current == null || isLaterExam(exam, current)) {
                latest.put(exam.getSchoolId(), exam);
            }
        }
        return latest;
    }

    /**
     * ponytail: 每校取 exam_date 最新的一场；日期相同或都为空时取 id 更大的，有日期的优先于无日期的，保证结果确定
     */
    private static boolean isLaterExam(Exam candidate, Exam current) {
        LocalDate a = candidate.getExamDate();
        LocalDate b = current.getExamDate();
        if (a != null && b != null && !a.isEqual(b)) {
            return a.isAfter(b);
        }
        if ((a == null) != (b == null)) {
            return a != null;
        }
        long candidateId = candidate.getId() == null ? Long.MIN_VALUE : candidate.getId();
        long currentId = current.getId() == null ? Long.MIN_VALUE : current.getId();
        return candidateId > currentId;
    }

    /**
     * 一所学校整场考试的总分口径汇总（与 getClassStat 一致：分母 = 至少一科有效成绩的学生数，线按本场各科折算求和）
     */
    private Summary schoolSummary(Exam exam, List<Score> scores, Map<Long, Subject> subjects) {
        Set<Long> subjectIds = ids(scores, Score::getSubjectId);
        Map<Long, ScoreLine> lines = subjectLines(exam.getId(), subjectIds, subjects);
        return summarize(new ArrayList<>(studentTotals(scores).values()), totalLine(scores, lines));
    }

    private RegionStatResponse.SchoolStat schoolStat(School school, Exam exam, Summary summary) {
        return RegionStatResponse.SchoolStat.builder()
                .schoolId(school.getId())
                .schoolName(school.getSchoolName())
                .examId(exam == null ? null : exam.getId())
                .examName(exam == null ? null : exam.getExamName())
                .studentCount(summary.count())
                .averageScore(summary.average())
                .passRate(summary.passRate())
                .excellentRate(summary.excellentRate())
                .build();
    }

    /**
     * 把某校各科均分并入跨校科目对比；科目按 subjectKey 跨校对齐
     */
    private void collectSubjectAverages(Map<String, RegionStatResponse.SubjectCompare> compares, Long schoolId,
                                        List<Score> validScores, Map<Long, Subject> subjects) {
        groupValues(validScores, Score::getSubjectId).forEach((subjectId, values) -> {
            Subject subject = subjects.get(subjectId);
            RegionStatResponse.SubjectCompare compare = compares.computeIfAbsent(subjectKey(subject, subjectId),
                    key -> RegionStatResponse.SubjectCompare.builder()
                            .subjectId(subjectId)
                            .subjectName(subject == null || subject.getSubjectName() == null
                                    ? "科目" + subjectId : subject.getSubjectName())
                            .schools(new ArrayList<>())
                            .build());
            compare.getSchools().add(RegionStatResponse.SchoolSubjectAverage.builder()
                    .schoolId(schoolId)
                    .averageScore(summarize(values).average())
                    .build());
        });
    }

    /**
     * ponytail: subject 是按校建的租户表，同一门课在各校 ID 不同；跨校按科目名称对齐（名称为空再退回 subject_code，再退回 ID），
     * 名称比编码更可能跨校一致（编码各校自拟如 MATH / SX），且前端图表分类轴本来就按名称显示
     */
    private static String subjectKey(Subject subject, Long subjectId) {
        if (subject != null) {
            if (subject.getSubjectName() != null && !subject.getSubjectName().trim().isEmpty()) {
                return "name:" + subject.getSubjectName().trim();
            }
            if (subject.getSubjectCode() != null && !subject.getSubjectCode().trim().isEmpty()) {
                return "code:" + subject.getSubjectCode().trim().toUpperCase(Locale.ROOT);
            }
        }
        return "id:" + subjectId;
    }

    /**
     * 按均分降序排名；并列同名次（名次 = 均分更高的学校数 + 1），与 calculateRankings 的并列规则一致
     */
    private static void assignSchoolRanks(List<RegionStatResponse.SchoolStat> ranked) {
        Comparator<RegionStatResponse.SchoolStat> byAverageDesc = Comparator.comparing(
                RegionStatResponse.SchoolStat::getAverageScore, Comparator.<BigDecimal>reverseOrder());
        ranked.sort(byAverageDesc.thenComparing(RegionStatResponse.SchoolStat::getSchoolId,
                Comparator.nullsLast(Comparator.<Long>naturalOrder())));
        for (int i = 0; i < ranked.size(); i++) {
            RegionStatResponse.SchoolStat current = ranked.get(i);
            boolean tied = i > 0 && current.getAverageScore().compareTo(ranked.get(i - 1).getAverageScore()) == 0;
            current.setRank(tied ? ranked.get(i - 1).getRank() : i + 1);
        }
    }

    private void assertSuperAdmin(String message) {
        if (!SecurityUtils.isSuperAdmin()) {
            throw new BusinessException(ResultCode.FORBIDDEN, message);
        }
    }

    // ---------------------------------------------------------------- 分数线

    /**
     * 一个口径（单科或总分）的满分 / 及格线 / 优秀线
     */
    private record ScoreLine(BigDecimal fullScore, BigDecimal passLine, BigDecimal excellentLine) {

        static ScoreLine sum(Collection<ScoreLine> lines) {
            BigDecimal fullScore = BigDecimal.ZERO;
            BigDecimal passLine = BigDecimal.ZERO;
            BigDecimal excellentLine = BigDecimal.ZERO;
            for (ScoreLine line : lines) {
                fullScore = fullScore.add(line.fullScore());
                passLine = passLine.add(line.passLine());
                excellentLine = excellentLine.add(line.excellentLine());
            }
            return new ScoreLine(fullScore, passLine, excellentLine);
        }
    }

    /**
     * 各科目的分数线。
     * 满分优先取 exam_subject.full_score（考试可单独设定），其次 subject.full_score，最后兜底 100；
     * 及格线取 subject.pass_score 按本场满分等比折算，为空则满分×60%；优秀线统一为满分×85%。
     */
    private Map<Long, ScoreLine> subjectLines(Long examId, Set<Long> subjectIds, Map<Long, Subject> subjects) {
        Map<Long, BigDecimal> examFullScores = examFullScores(examId);
        // ponytail: K1 —— 比例每次统计只读一次，而不是每科都打一次 sys_config
        BigDecimal passRatio = passRatio();
        BigDecimal excellentRatio = excellentRatio();
        Map<Long, ScoreLine> lines = new HashMap<>();
        for (Long subjectId : subjectIds) {
            if (subjectId == null) {
                continue;
            }
            Subject subject = subjects.get(subjectId);
            BigDecimal fullScore = firstPositive(examFullScores.get(subjectId),
                    subject == null ? null : subject.getFullScore());
            lines.put(subjectId, new ScoreLine(fullScore, passLineOf(subject, fullScore, passRatio),
                    ratioOf(fullScore, excellentRatio)));
        }
        return lines;
    }

    /**
     * 及格比例：sys_config score.pass.line / 100，配置服务返回空或非正数时兜底 0.60
     */
    private BigDecimal passRatio() {
        BigDecimal ratio = scoreRuleService.passRatio();
        return ratio == null || ratio.signum() <= 0 ? DEFAULT_PASS_RATIO : ratio;
    }

    /**
     * 优秀比例：sys_config score.excellent.line / 100，配置服务返回空或非正数时兜底 0.85
     */
    private BigDecimal excellentRatio() {
        BigDecimal ratio = scoreRuleService.excellentRatio();
        return ratio == null || ratio.signum() <= 0 ? DEFAULT_EXCELLENT_RATIO : ratio;
    }

    /**
     * 单科及格线。
     * ponytail: subject.pass_score 是相对 subject.full_score 配置的，本场 exam_subject.full_score 不同时按比例折算
     * （passScore × examFull / subjectFull，HALF_UP 2 位）；否则与已按本场满分折算的优秀线口径不一致，极端时满分也「不及格」。
     * 未配置及格分或科目满分非法时退回满分×及格比例（K1，默认 60%）
     */
    private BigDecimal passLineOf(Subject subject, BigDecimal fullScore, BigDecimal passRatio) {
        if (subject == null || subject.getPassScore() == null) {
            return ratioOf(fullScore, passRatio);
        }
        BigDecimal subjectFull = subject.getFullScore();
        if (subjectFull == null || subjectFull.signum() <= 0) {
            return ratioOf(fullScore, passRatio);
        }
        if (subjectFull.compareTo(fullScore) == 0) {
            return subject.getPassScore();
        }
        return subject.getPassScore().multiply(fullScore).divide(subjectFull, 2, RoundingMode.HALF_UP);
    }

    /**
     * 某次考试 exam_subject 里配置的各科满分
     */
    private Map<Long, BigDecimal> examFullScores(Long examId) {
        Map<Long, BigDecimal> fullScores = new HashMap<>();
        if (examId == null) {
            return fullScores;
        }
        List<ExamSubject> examSubjects = examSubjectMapper.selectByExamId(examId);
        if (examSubjects == null) {
            return fullScores;
        }
        for (ExamSubject examSubject : examSubjects) {
            if (examSubject.getSubjectId() != null && examSubject.getFullScore() != null) {
                fullScores.put(examSubject.getSubjectId(), examSubject.getFullScore());
            }
        }
        return fullScores;
    }

    /**
     * 一组成绩涉及的科目合计出的总分口径
     */
    private ScoreLine totalLine(Collection<Score> scores, Map<Long, ScoreLine> lines) {
        List<ScoreLine> involved = ids(scores, Score::getSubjectId).stream()
                .map(lines::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        return ScoreLine.sum(involved);
    }

    private BigDecimal fullScoreOf(Map<Long, ScoreLine> lines, Long subjectId) {
        ScoreLine line = subjectId == null ? null : lines.get(subjectId);
        return line == null ? DEFAULT_FULL_SCORE : line.fullScore();
    }

    private BigDecimal firstPositive(BigDecimal... candidates) {
        for (BigDecimal candidate : candidates) {
            if (candidate != null && candidate.signum() > 0) {
                return candidate;
            }
        }
        return DEFAULT_FULL_SCORE;
    }

    private BigDecimal ratioOf(BigDecimal fullScore, BigDecimal ratio) {
        return fullScore.multiply(ratio).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 得分率(%)，保留 1 位小数；满分非法时按 100 折算
     */
    private BigDecimal percentage(BigDecimal score, BigDecimal fullScore) {
        BigDecimal base = fullScore == null || fullScore.signum() <= 0 ? DEFAULT_FULL_SCORE : fullScore;
        return score.multiply(HUNDRED).divide(base, 1, RoundingMode.HALF_UP);
    }

    // ---------------------------------------------------------------- 总分名次

    private record TotalRank(Integer rankInClass, Integer rankInGrade) {
    }

    /**
     * 按总分现算该生的班级/年级名次（并列同名次：名次 = 总分更高的人数 + 1）。
     * 全科缺考的学生没有名次。
     */
    private TotalRank totalRank(List<Score> examScores, Long studentId, boolean hasValidScore) {
        if (!hasValidScore) {
            return new TotalRank(null, null);
        }
        Map<Long, BigDecimal> totals = studentTotals(examScores);
        BigDecimal mine = totals.getOrDefault(studentId, BigDecimal.ZERO);
        Map<Long, Long> studentClass = examScores.stream()
                .filter(s -> s.getClassId() != null)
                .collect(Collectors.toMap(Score::getStudentId, Score::getClassId, (a, b) -> a));
        Long classId = studentClass.get(studentId);
        if (classId == null) {
            return new TotalRank(null, null);
        }

        Map<Long, SchoolClass> classes = byId(classMapper, new HashSet<>(studentClass.values()), SchoolClass::getId);
        Long gradeId = gradeIdOf(classes, classId);

        int rankInClass = rankAmong(totals, mine, sid -> classId.equals(studentClass.get(sid)));
        Integer rankInGrade = gradeId == null ? null :
                rankAmong(totals, mine, sid -> gradeId.equals(gradeIdOf(classes, studentClass.get(sid))));
        return new TotalRank(rankInClass, rankInGrade);
    }

    private int rankAmong(Map<Long, BigDecimal> totals, BigDecimal mine, Predicate<Long> inGroup) {
        long higher = totals.entrySet().stream()
                .filter(entry -> inGroup.test(entry.getKey()))
                .filter(entry -> entry.getValue().compareTo(mine) > 0)
                .count();
        return (int) higher + 1;
    }

    /**
     * ponytail: 契约 R —— score 行上存好的总分名次（同一学生各行相同），历史数据未重算时为 null
     */
    private TotalRank storedTotalRank(List<Score> rows) {
        return rows.stream()
                .filter(s -> s.getTotalRankInClass() != null)
                .findFirst()
                .map(s -> new TotalRank(s.getTotalRankInClass(), s.getTotalRankInGrade()))
                .orElse(null);
    }

    /**
     * 该生某场考试的总分名次：优先读行上存的 totalRank*，为空再用全场成绩现算；全科缺考没有名次
     */
    private TotalRank totalRankOf(List<Score> rows, Long studentId, boolean hasValidScore,
                                  Supplier<List<Score>> examScores) {
        if (!hasValidScore) {
            return new TotalRank(null, null);
        }
        TotalRank stored = storedTotalRank(rows);
        return stored != null ? stored : totalRank(examScores.get(), studentId, true);
    }

    // ---------------------------------------------------------------- 聚合辅助

    /**
     * 一组分数的汇总：人数/总分/平均/最高/最低/及格率/优秀率
     */
    private record Summary(int count, BigDecimal total, BigDecimal average, BigDecimal max, BigDecimal min,
                           BigDecimal passRate, BigDecimal excellentRate) {
    }

    /**
     * 不关心及格/优秀率的汇总（趋势、年级均分等），两率为 0
     */
    private Summary summarize(List<BigDecimal> values) {
        return summarize(values, null);
    }

    private Summary summarize(List<BigDecimal> values, ScoreLine line) {
        if (values.isEmpty()) {
            return new Summary(0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO);
        }
        BigDecimal total = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        long passCount = line == null ? 0 :
                values.stream().filter(v -> v.compareTo(line.passLine()) >= 0).count();
        long excellentCount = line == null ? 0 :
                values.stream().filter(v -> v.compareTo(line.excellentLine()) >= 0).count();
        return new Summary(
                values.size(),
                total,
                total.divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP),
                Collections.max(values),
                Collections.min(values),
                rate(passCount, values.size()),
                rate(excellentCount, values.size()));
    }

    private BigDecimal rate(long part, int total) {
        return total <= 0 ? BigDecimal.ZERO :
                BigDecimal.valueOf(part).multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    /**
     * 总体标准差
     */
    private BigDecimal standardDeviation(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        double mean = values.stream().mapToDouble(BigDecimal::doubleValue).average().orElse(0);
        double variance = values.stream()
                .mapToDouble(v -> Math.pow(v.doubleValue() - mean, 2))
                .average()
                .orElse(0);
        return BigDecimal.valueOf(Math.sqrt(variance)).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 线性插值分位数，sorted 须升序且非空
     */
    private BigDecimal percentile(List<BigDecimal> sorted, double p) {
        double position = p * (sorted.size() - 1);
        int lower = (int) Math.floor(position);
        int upper = (int) Math.ceil(position);
        BigDecimal fraction = BigDecimal.valueOf(position - lower);
        return sorted.get(lower)
                .add(sorted.get(upper).subtract(sorted.get(lower)).multiply(fraction))
                .setScale(2, RoundingMode.HALF_UP);
    }

    // ponytail: is_absent 在 DDL 里可空（SMALLINT DEFAULT 0），直接 == 1 拆箱会 NPE，统一走空安全比较
    private static boolean absent(Score score) {
        return Integer.valueOf(1).equals(score.getIsAbsent());
    }

    private static boolean isPublished(Exam exam) {
        return exam != null && Integer.valueOf(EXAM_STATUS_PUBLISHED).equals(exam.getStatus());
    }

    private List<Score> validScores(List<Score> scores) {
        return scores.stream()
                .filter(s -> s.getScore() != null && (s.getIsAbsent() == null || s.getIsAbsent() == 0))
                .collect(Collectors.toList());
    }

    private List<BigDecimal> scoreValues(List<Score> scores) {
        return scores.stream().map(Score::getScore).collect(Collectors.toList());
    }

    /**
     * 有效成绩按学生求总分
     */
    private Map<Long, BigDecimal> studentTotals(List<Score> scores) {
        return validScores(scores).stream()
                .collect(Collectors.groupingBy(
                        Score::getStudentId,
                        Collectors.reducing(BigDecimal.ZERO, Score::getScore, BigDecimal::add)
                ));
    }

    /**
     * 有效成绩按 key 分组取分值，key 为空的跳过；按 key 升序
     */
    private Map<Long, List<BigDecimal>> groupValues(List<Score> validScores, Function<Score, Long> key) {
        Map<Long, List<BigDecimal>> grouped = new TreeMap<>();
        for (Score score : validScores) {
            Long k = key.apply(score);
            if (k != null) {
                grouped.computeIfAbsent(k, x -> new ArrayList<>()).add(score.getScore());
            }
        }
        return grouped;
    }

    /**
     * 科目名称 -> 得分，按科目ID排序
     */
    private Map<String, BigDecimal> subjectScoreMap(List<Score> validScores, Map<Long, String> subjectNames) {
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        validScores.stream()
                .sorted(Comparator.comparing(Score::getSubjectId))
                .forEach(s -> map.put(subjectNames.get(s.getSubjectId()), s.getScore()));
        return map;
    }

    private List<ClassStatResponse.TopStudent> topStudents(Map<Long, BigDecimal> studentTotalScores,
                                                          List<Score> validScores,
                                                          Map<Long, String> subjectNames) {
        List<Map.Entry<Long, BigDecimal>> ranked = studentTotalScores.entrySet().stream()
                .sorted(Map.Entry.<Long, BigDecimal>comparingByValue().reversed())
                .limit(TOP_STUDENT_LIMIT)
                .collect(Collectors.toList());
        Set<Long> studentIds = ranked.stream().map(Map.Entry::getKey).collect(Collectors.toSet());
        Map<Long, Student> students = byId(studentMapper, studentIds, Student::getId);
        Map<Long, List<Score>> scoresByStudent = validScores.stream()
                .collect(Collectors.groupingBy(Score::getStudentId));

        List<ClassStatResponse.TopStudent> topStudents = new ArrayList<>();
        for (int i = 0; i < ranked.size(); i++) {
            Long studentId = ranked.get(i).getKey();
            Student student = students.get(studentId);
            topStudents.add(ClassStatResponse.TopStudent.builder()
                    .rank(i + 1)
                    .studentId(studentId)
                    .studentNo(student == null ? null : student.getStudentNo())
                    .studentName(student == null || student.getName() == null ? "学生" + studentId : student.getName())
                    .totalScore(ranked.get(i).getValue())
                    .subjectScores(subjectScoreMap(scoresByStudent.get(studentId), subjectNames))
                    .build());
        }
        return topStudents;
    }

    // ---------------------------------------------------------------- 名称查询辅助

    private Set<Long> ids(Collection<Score> scores, Function<Score, Long> getter) {
        return scores.stream().map(getter).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private Set<Long> gradeIds(Map<Long, SchoolClass> classes) {
        return classes.values().stream().map(SchoolClass::getGradeId).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private <T> Map<Long, T> byId(BaseMapper<T> mapper, Set<Long> ids, Function<T, Long> idGetter) {
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return mapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(idGetter, Function.identity()));
    }

    /**
     * id -> 名称；查不到的用 fallbackPrefix + id 兜底，保证每个传入 id 都有值
     */
    private <T> Map<Long, String> names(BaseMapper<T> mapper, Set<Long> ids, Function<T, Long> idGetter,
                                        Function<T, String> nameGetter, String fallbackPrefix) {
        return names(byId(mapper, ids, idGetter), ids, nameGetter, fallbackPrefix);
    }

    /**
     * 同上，但实体已在手（避免为名称和分数线各查一遍 subject）
     */
    private <T> Map<Long, String> names(Map<Long, T> entities, Set<Long> ids,
                                        Function<T, String> nameGetter, String fallbackPrefix) {
        Map<Long, String> names = new HashMap<>();
        for (Long id : ids) {
            T entity = entities.get(id);
            String name = entity == null ? null : nameGetter.apply(entity);
            names.put(id, name == null ? fallbackPrefix + id : name);
        }
        return names;
    }

    private String className(Map<Long, SchoolClass> classes, Long classId) {
        SchoolClass schoolClass = classes.get(classId);
        return schoolClass == null || schoolClass.getClassName() == null ? "班级" + classId : schoolClass.getClassName();
    }

    private Long gradeIdOf(Map<Long, SchoolClass> classes, Long classId) {
        SchoolClass schoolClass = classes.get(classId);
        return schoolClass == null ? null : schoolClass.getGradeId();
    }

    // ---------------------------------------------------------------- 数据范围

    /**
     * 年级下的班级ID集合；gradeId 为空返回 null 表示不按年级过滤
     */
    private Set<Long> gradeClassIds(Long gradeId, Long schoolId) {
        if (gradeId == null) {
            return null;
        }
        // score 表没有 grade_id，按 class.grade_id 反查；schoolId 为 null 仅超管（不限校）
        List<SchoolClass> classes = classMapper.selectList(new LambdaQueryWrapper<SchoolClass>()
                .eq(SchoolClass::getGradeId, gradeId)
                .eq(schoolId != null, SchoolClass::getSchoolId, schoolId));
        return classes.stream().map(SchoolClass::getId).collect(Collectors.toSet());
    }

    /**
     * 教师可见的班级集合；管理员返回 null 表示不限制；学生直接拒绝。
     * subjectId 非空时为「任教该科目的班级 ∪ 班主任班级」，为空时为全部可访问班级（任教 ∪ 班主任）。
     */
    private Set<Long> teacherVisibleClassIds(Long subjectId) {
        // ponytail: 学生不是「不限制」而是没有班级/年级/科目聚合的权限，原来返回 null 会放行全校数据
        assertNotStudent();
        if (!SecurityUtils.isTeacher()) {
            return null;
        }
        if (subjectId == null) {
            return teacherScopeService.currentAllowedClassIds();
        }
        Set<Long> classIds = teacherScopeService.currentTeachingAssignments().stream()
                .filter(tc -> subjectId.equals(tc.getSubjectId()))
                .map(TeacherClass::getClassId)
                .collect(Collectors.toCollection(HashSet::new));
        classIds.addAll(teacherScopeService.currentHeadClassIds());
        return classIds;
    }

    /**
     * 只保留 classIds 内班级的成绩；classIds 为 null 表示不过滤
     */
    private List<Score> limitToClasses(List<Score> scores, Set<Long> classIds) {
        if (classIds == null) {
            return scores;
        }
        return scores.stream()
                .filter(s -> classIds.contains(s.getClassId()))
                .collect(Collectors.toList());
    }

    /**
     * 学生账号没有班级/年级/科目维度的聚合统计权限
     */
    private void assertNotStudent() {
        if (SecurityUtils.isStudent()) {
            throw new BusinessException(ResultCode.FORBIDDEN, "学生账号无权查看班级、年级或科目统计");
        }
    }

    /**
     * 学生只能查自己（studentId 必须等于登录学生档案 ID）
     */
    private void assertStudentSelfOnly(Long studentId, String message) {
        if (SecurityUtils.isStudent() && !SecurityUtils.currentStudentId().equals(studentId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, message);
        }
    }

    /**
     * 教师数据范围校验（与 ScoreServiceImpl.checkTeacherScope 逻辑一致）
     */
    private void checkTeacherScope(Long classId, Long subjectId) {
        // ponytail: 与 ScoreServiceImpl.checkTeacherScope 对齐，学生先拒绝（原来漏掉后 !isTeacher() 直接放行）
        assertNotStudent();
        if (!SecurityUtils.isTeacher()) return;
        if (classId == null) {
            throw new BusinessException(ResultCode.FORBIDDEN, "教师账号无权查询该统计范围");
        }
        if (subjectId != null) {
            if (!teacherScopeService.isAllowedClassSubject(classId, subjectId)) {
                throw new BusinessException(ResultCode.FORBIDDEN, "无权访问该班级或科目的统计数据");
            }
        } else {
            if (!teacherScopeService.currentAllowedClassIds().contains(classId)) {
                throw new BusinessException(ResultCode.FORBIDDEN, "无权访问该班级的统计数据");
            }
        }
    }

    /**
     * 计算分数段分布
     * ponytail: 分段按得分率（分数/满分×100）划分成 0-60/60-70/70-80/80-90/90-100 五段，标签仍是百分比区间，前端不用改
     */
    private List<ClassStatResponse.ScoreRange> calculateScoreRanges(List<BigDecimal> scores, BigDecimal fullScore) {
        BigDecimal base = fullScore == null || fullScore.signum() <= 0 ? DEFAULT_FULL_SCORE : fullScore;
        Map<String, Integer> rangeMap = new LinkedHashMap<>();
        rangeMap.put("0-60", 0);
        rangeMap.put("60-70", 0);
        rangeMap.put("70-80", 0);
        rangeMap.put("80-90", 0);
        rangeMap.put("90-100", 0);

        for (BigDecimal score : scores) {
            int percent = score.multiply(HUNDRED).divide(base, 2, RoundingMode.HALF_UP).intValue();
            if (percent < 60) {
                rangeMap.put("0-60", rangeMap.get("0-60") + 1);
            } else if (percent < 70) {
                rangeMap.put("60-70", rangeMap.get("60-70") + 1);
            } else if (percent < 80) {
                rangeMap.put("70-80", rangeMap.get("70-80") + 1);
            } else if (percent < 90) {
                rangeMap.put("80-90", rangeMap.get("80-90") + 1);
            } else {
                rangeMap.put("90-100", rangeMap.get("90-100") + 1);
            }
        }

        int total = scores.size();
        return rangeMap.entrySet().stream()
                .map(entry -> ClassStatResponse.ScoreRange.builder()
                        .range(entry.getKey())
                        .count(entry.getValue())
                        .percentage(rate(entry.getValue(), total))
                        .build())
                .collect(Collectors.toList());
    }

}
