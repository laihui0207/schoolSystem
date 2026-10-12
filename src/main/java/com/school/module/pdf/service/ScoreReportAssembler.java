package com.school.module.pdf.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.school.module.class_module.entity.SchoolClass;
import com.school.module.class_module.mapper.ClassMapper;
import com.school.module.exam.entity.Exam;
import com.school.module.exam.entity.ExamSubject;
import com.school.module.exam.mapper.ExamSubjectMapper;
import com.school.module.grade.entity.Grade;
import com.school.module.grade.mapper.GradeMapper;
import com.school.module.pdf.dto.ReportContext;
import com.school.module.pdf.dto.ReportContext.GroupAverages;
import com.school.module.pdf.dto.ReportContext.SubjectMeta;
import com.school.module.pdf.dto.ScoreReportData;
import com.school.module.school.entity.School;
import com.school.module.school.mapper.SchoolMapper;
import com.school.module.score.entity.Score;
import com.school.module.score.mapper.ScoreMapper;
import com.school.module.student.entity.Student;
import com.school.module.subject.entity.Subject;
import com.school.module.subject.mapper.SubjectMapper;
import com.school.module.term.entity.AcademicTerm;
import com.school.module.term.mapper.AcademicTermMapper;
import com.school.security.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 成绩单数据装配：把考试/班级/科目/成绩拼成渲染层需要的 {@link ScoreReportData}
 * <p>
 * ponytail: 不复用 StatService.getClassStat 取班级均分——它对学生账号整体拒绝（assertNotStudent），
 * 而学生下载自己的成绩单也要看到班级/年级均分对照；这里直接从成绩行现算，口径与 StatService 一致
 * （有效成绩 = score 非空且未缺考；均分 = Σ学生总分 / 有有效成绩的学生数，HALF_UP 2 位）。
 *
 * @author School System
 */
@Component
@RequiredArgsConstructor
public class ScoreReportAssembler {

    private static final BigDecimal DEFAULT_FULL_SCORE = BigDecimal.valueOf(100);

    private final SchoolMapper schoolMapper;
    private final AcademicTermMapper academicTermMapper;
    private final ClassMapper classMapper;
    private final GradeMapper gradeMapper;
    private final ExamSubjectMapper examSubjectMapper;
    private final SubjectMapper subjectMapper;
    private final ScoreMapper scoreMapper;

    /**
     * 构建某班某场考试的共享上下文
     *
     * @param exam        考试（已校验存在且已发布）
     * @param classId     班级
     * @param classScores 该班本场全部成绩行（调用方已查好，避免重复查询）
     */
    public ReportContext buildContext(Exam exam, Long classId, List<Score> classScores) {
        School school = exam.getSchoolId() == null ? null : schoolMapper.selectById(exam.getSchoolId());
        AcademicTerm term = exam.getTermId() == null ? null : academicTermMapper.selectById(exam.getTermId());
        SchoolClass schoolClass = classId == null ? null : classMapper.selectById(classId);
        Grade grade = schoolClass == null || schoolClass.getGradeId() == null
                ? null : gradeMapper.selectById(schoolClass.getGradeId());

        List<SubjectMeta> subjects = subjectMetas(exam.getId(), classScores);
        GroupAverages classAverages = averagesOf(classScores);
        GroupAverages gradeAverages = gradeAverages(exam, schoolClass, classScores, classAverages);

        return new ReportContext(
                exam,
                school == null || school.getSchoolName() == null ? "" : school.getSchoolName(),
                termName(term),
                schoolClass,
                grade == null || grade.getGradeName() == null ? "" : grade.getGradeName(),
                subjects,
                classScores,
                classAverages,
                gradeAverages);
    }

    /**
     * 装配一名学生的成绩单
     *
     * @param context     班级上下文
     * @param student     学生档案（可为 null，此时姓名/学号留空）
     * @param studentRows 该生本场成绩行
     * @param generatedAt 生成时间
     */
    public ScoreReportData buildStudentReport(ReportContext context, Student student,
                                              List<Score> studentRows, LocalDateTime generatedAt) {
        Map<Long, Score> rowBySubject = studentRows.stream()
                .filter(s -> s.getSubjectId() != null)
                .collect(Collectors.toMap(Score::getSubjectId, s -> s, (a, b) -> a, LinkedHashMap::new));

        List<ScoreReportData.SubjectRow> rows = new ArrayList<>();
        BigDecimal totalFull = BigDecimal.ZERO;
        for (SubjectMeta meta : context.subjects()) {
            rows.add(subjectRow(meta, rowBySubject.remove(meta.subjectId()), context));
            totalFull = totalFull.add(meta.fullScore());
        }
        // ponytail: 成绩行里出现了 exam_subject 之外的科目（考试科目后来被删改）也照样列出，不让学生的分数凭空消失
        for (Score orphan : rowBySubject.values()) {
            SubjectMeta meta = new SubjectMeta(orphan.getSubjectId(), "科目" + orphan.getSubjectId(), DEFAULT_FULL_SCORE);
            rows.add(subjectRow(meta, orphan, context));
            totalFull = totalFull.add(DEFAULT_FULL_SCORE);
        }

        BigDecimal total = validScores(studentRows).stream()
                .map(Score::getScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // ponytail: 契约 R —— 总分名次读 Score.totalRank*（同一学生各行相同），为空说明历史数据未重算，PDF 显示「-」而不是现算一个不一致的数
        Score ranked = studentRows.stream()
                .filter(s -> s.getTotalRankInClass() != null || s.getTotalRankInGrade() != null || s.getTotalRankInSchool() != null)
                .findFirst()
                .orElse(null);

        Exam exam = context.exam();
        return ScoreReportData.builder()
                .schoolName(context.schoolName())
                .examName(exam.getExamName() == null ? "" : exam.getExamName())
                .termName(context.termName())
                .examDate(exam.getExamDate() == null ? "" : exam.getExamDate().toString())
                .studentName(student == null || student.getName() == null ? "" : student.getName())
                .studentNo(student == null || student.getStudentNo() == null ? "" : student.getStudentNo())
                .className(context.className())
                .gradeName(context.gradeName())
                .subjectRows(rows)
                .totalScore(total)
                .totalFullScore(totalFull)
                .totalRankInClass(ranked == null ? null : ranked.getTotalRankInClass())
                .totalRankInGrade(ranked == null ? null : ranked.getTotalRankInGrade())
                .totalRankInSchool(ranked == null ? null : ranked.getTotalRankInSchool())
                .classAverage(context.classAverages().totalAverage())
                .gradeAverage(context.gradeAverages().totalAverage())
                .generatedAt(generatedAt)
                .build();
    }

    // ---------------------------------------------------------------- 科目

    /**
     * 考试科目及满分：exam_subject 按 sort_order 排序；满分优先 exam_subject.full_score，其次 subject.full_score，兜底 100
     */
    private List<SubjectMeta> subjectMetas(Long examId, List<Score> classScores) {
        List<ExamSubject> examSubjects = examSubjectMapper.selectByExamId(examId);
        List<ExamSubject> ordered = examSubjects == null ? new ArrayList<>() : new ArrayList<>(examSubjects);
        Comparator<ExamSubject> bySortOrder = Comparator.comparing(ExamSubject::getSortOrder,
                Comparator.nullsLast(Comparator.<Integer>naturalOrder()));
        ordered.sort(bySortOrder.thenComparing(ExamSubject::getId, Comparator.nullsLast(Comparator.<Long>naturalOrder())));

        Set<Long> subjectIds = ordered.stream().map(ExamSubject::getSubjectId).filter(Objects::nonNull)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        classScores.stream().map(Score::getSubjectId).filter(Objects::nonNull).forEach(subjectIds::add);
        Map<Long, Subject> subjects = subjectIds.isEmpty() ? Map.of()
                : subjectMapper.selectBatchIds(subjectIds).stream()
                        .collect(Collectors.toMap(Subject::getId, s -> s, (a, b) -> a));

        List<SubjectMeta> metas = new ArrayList<>();
        Set<Long> seen = new java.util.HashSet<>();
        for (ExamSubject es : ordered) {
            if (es.getSubjectId() == null || !seen.add(es.getSubjectId())) {
                continue;
            }
            Subject subject = subjects.get(es.getSubjectId());
            metas.add(new SubjectMeta(es.getSubjectId(), subjectName(subject, es.getSubjectId()),
                    firstPositive(es.getFullScore(), subject == null ? null : subject.getFullScore())));
        }
        // 考试未配置 exam_subject 但成绩里有的科目，按成绩出现顺序补在后面
        for (Long subjectId : subjectIds) {
            if (seen.add(subjectId)) {
                Subject subject = subjects.get(subjectId);
                metas.add(new SubjectMeta(subjectId, subjectName(subject, subjectId),
                        firstPositive(subject == null ? null : subject.getFullScore())));
            }
        }
        return metas;
    }

    private ScoreReportData.SubjectRow subjectRow(SubjectMeta meta, Score row, ReportContext context) {
        boolean absent = row != null && Integer.valueOf(1).equals(row.getIsAbsent());
        return ScoreReportData.SubjectRow.builder()
                .subjectId(meta.subjectId())
                .subjectName(meta.subjectName())
                .fullScore(meta.fullScore())
                .score(row == null || absent ? null : row.getScore())
                .absent(absent)
                .missing(row == null)
                .rankInClass(row == null ? null : row.getRankInClass())
                .rankInGrade(row == null ? null : row.getRankInGrade())
                .rankInSchool(row == null ? null : row.getRankInSchool())
                .classAverage(context.classAverages().subjectAverage(meta.subjectId()))
                .gradeAverage(context.gradeAverages().subjectAverage(meta.subjectId()))
                .build();
    }

    // ---------------------------------------------------------------- 均分

    /**
     * 年级均分：按 class.grade_id 找同年级班级，再取这些班级的本场成绩。
     * 班级无年级归属时退化为班级均分；同年级只有本班时直接复用，少一次查询。
     */
    private GroupAverages gradeAverages(Exam exam, SchoolClass schoolClass, List<Score> classScores,
                                        GroupAverages classAverages) {
        if (schoolClass == null || schoolClass.getGradeId() == null) {
            return classAverages;
        }
        Set<Long> gradeClassIds = classMapper.selectList(new LambdaQueryWrapper<SchoolClass>()
                        .eq(SchoolClass::getGradeId, schoolClass.getGradeId())
                        .eq(schoolClass.getSchoolId() != null, SchoolClass::getSchoolId, schoolClass.getSchoolId()))
                .stream().map(SchoolClass::getId).collect(Collectors.toSet());
        if (gradeClassIds.size() <= 1) {
            return classAverages;
        }
        List<Score> gradeScores = scoreMapper.selectByExamId(SecurityUtils.currentSchoolIdForQuery(), exam.getId(), null)
                .stream()
                .filter(s -> gradeClassIds.contains(s.getClassId()))
                .collect(Collectors.toList());
        return gradeScores.isEmpty() ? GroupAverages.empty() : averagesOf(gradeScores);
    }

    private GroupAverages averagesOf(Collection<Score> scores) {
        List<Score> valid = validScores(scores);
        if (valid.isEmpty()) {
            return GroupAverages.empty();
        }
        Map<Long, BigDecimal> totals = valid.stream().collect(Collectors.groupingBy(
                Score::getStudentId, Collectors.reducing(BigDecimal.ZERO, Score::getScore, BigDecimal::add)));
        BigDecimal totalAverage = average(totals.values());

        Map<Long, List<BigDecimal>> bySubject = new HashMap<>();
        for (Score score : valid) {
            if (score.getSubjectId() != null) {
                bySubject.computeIfAbsent(score.getSubjectId(), k -> new ArrayList<>()).add(score.getScore());
            }
        }
        Map<Long, BigDecimal> subjectAverages = new HashMap<>();
        bySubject.forEach((subjectId, values) -> subjectAverages.put(subjectId, average(values)));
        return new GroupAverages(totalAverage, subjectAverages);
    }

    private static BigDecimal average(Collection<BigDecimal> values) {
        if (values.isEmpty()) {
            return null;
        }
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }

    // ponytail: is_absent 可空（SMALLINT DEFAULT 0），与 StatServiceImpl.validScores 同口径，空安全比较
    private static List<Score> validScores(Collection<Score> scores) {
        return scores.stream()
                .filter(s -> s.getScore() != null && (s.getIsAbsent() == null || s.getIsAbsent() == 0))
                .filter(s -> s.getStudentId() != null)
                .collect(Collectors.toList());
    }

    // ---------------------------------------------------------------- 小工具

    private static String termName(AcademicTerm term) {
        if (term == null) {
            return "";
        }
        String year = term.getYear() == null ? "" : term.getYear() + "学年";
        String semester = term.getTerm() == null ? "" : "第" + term.getTerm() + "学期";
        return (year + " " + semester).trim();
    }

    private static String subjectName(Subject subject, Long subjectId) {
        return subject == null || subject.getSubjectName() == null ? "科目" + subjectId : subject.getSubjectName();
    }

    private static BigDecimal firstPositive(BigDecimal... candidates) {
        for (BigDecimal candidate : candidates) {
            if (candidate != null && candidate.signum() > 0) {
                return candidate;
            }
        }
        return DEFAULT_FULL_SCORE;
    }

}
