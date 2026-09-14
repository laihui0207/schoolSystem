package com.school.module.stat.service;

import com.school.common.exception.BusinessException;
import com.school.module.score.entity.Score;
import com.school.module.score.mapper.ScoreMapper;
import com.school.module.stat.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 成绩统计服务实现类
 *
 * @author School System
 */
@Service
@RequiredArgsConstructor
public class StatServiceImpl implements StatService {

    private final ScoreMapper scoreMapper;

    @Override
    public PersonalStatResponse getPersonalStat(Long examId, Long studentId) {
        // 查询学生该次考试的所有成绩
        List<Score> scores = scoreMapper.selectByExamId(examId, null).stream()
                .filter(s -> s.getStudentId().equals(studentId))
                .collect(Collectors.toList());

        if (scores.isEmpty()) {
            throw new BusinessException("未找到学生成绩");
        }

        // 计算总分
        BigDecimal totalScore = scores.stream()
                .filter(s -> s.getIsAbsent() == 0 && s.getScore() != null)
                .map(Score::getScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 计算平均分
        long validCount = scores.stream()
                .filter(s -> s.getIsAbsent() == 0 && s.getScore() != null)
                .count();

        BigDecimal averageScore = validCount > 0 ?
                totalScore.divide(BigDecimal.valueOf(validCount), 2, RoundingMode.HALF_UP) :
                BigDecimal.ZERO;

        // 构建科目成绩列表
        List<PersonalStatResponse.SubjectScore> subjectScores = scores.stream()
                .map(score -> PersonalStatResponse.SubjectScore.builder()
                        .subjectId(score.getSubjectId())
                        .score(score.getScore())
                        .rankInClass(score.getRankInClass())
                        .rankInGrade(score.getRankInGrade())
                        .isAbsent(score.getIsAbsent() == 1)
                        .build())
                .collect(Collectors.toList());

        Score firstScore = scores.get(0);
        return PersonalStatResponse.builder()
                .studentId(studentId)
                .examId(examId)
                .totalScore(totalScore)
                .averageScore(averageScore)
                .rankInClass(firstScore.getRankInClass())
                .rankInGrade(firstScore.getRankInGrade())
                .subjectScores(subjectScores)
                .build();
    }

    @Override
    public StudentTrendResponse getStudentTrend(Long studentId, Long subjectId) {
        return StudentTrendResponse.builder()
                .studentId(studentId)
                .examTrends(new ArrayList<>())
                .subjectRadars(new ArrayList<>())
                .build();
    }

    @Override
    public StudentTrendResponse getStudentTrend(Long studentId, Long subjectId, Long termId) {
        // ponytail: termId filter deferred — add when trend query needs term scoping
        return getStudentTrend(studentId, subjectId);
    }

    @Override
    public ClassStatResponse getClassStat(Long examId, Long classId) {
        // 查询该班级该次考试的所有成绩
        List<Score> scores = scoreMapper.selectByExamId(examId, classId);

        if (scores.isEmpty()) {
            throw new BusinessException("未找到班级成绩");
        }

        // 计算基础统计数据
        int totalStudents = (int) scores.stream()
                .map(Score::getStudentId)
                .distinct()
                .count();

        int absentStudents = (int) scores.stream()
                .filter(s -> s.getIsAbsent() == 1)
                .map(Score::getStudentId)
                .distinct()
                .count();

        int actualStudents = totalStudents - absentStudents;

        // 过滤有效成绩
        List<Score> validScores = scores.stream()
                .filter(s -> s.getIsAbsent() == 0 && s.getScore() != null)
                .collect(Collectors.toList());

        if (validScores.isEmpty()) {
            return ClassStatResponse.builder()
                    .classId(classId)
                    .examId(examId)
                    .totalStudents(totalStudents)
                    .actualStudents(actualStudents)
                    .absentStudents(absentStudents)
                    .subjectStats(new ArrayList<>())
                    .scoreRanges(new ArrayList<>())
                    .build();
        }

        // 计算总分（按学生分组求和）
        Map<Long, BigDecimal> studentTotalScores = validScores.stream()
                .collect(Collectors.groupingBy(
                        Score::getStudentId,
                        Collectors.reducing(BigDecimal.ZERO, Score::getScore, BigDecimal::add)
                ));

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

        // 计算及格率和优秀率（假设总分按百分制折算）
        long passCount = studentTotalScores.values().stream()
                .filter(score -> score.compareTo(BigDecimal.valueOf(60)) >= 0)
                .count();

        long excellentCount = studentTotalScores.values().stream()
                .filter(score -> score.compareTo(BigDecimal.valueOf(85)) >= 0)
                .count();

        BigDecimal passRate = actualStudents > 0 ?
                BigDecimal.valueOf(passCount).multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(actualStudents), 2, RoundingMode.HALF_UP) :
                BigDecimal.ZERO;

        BigDecimal excellentRate = actualStudents > 0 ?
                BigDecimal.valueOf(excellentCount).multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(actualStudents), 2, RoundingMode.HALF_UP) :
                BigDecimal.ZERO;

        // 计算分数段分布
        List<ClassStatResponse.ScoreRange> scoreRanges = calculateScoreRanges(
                new ArrayList<>(studentTotalScores.values())
        );

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
                .subjectStats(new ArrayList<>())
                .scoreRanges(scoreRanges)
                .build();
    }

    @Override
    public GradeStatResponse getGradeStat(Long examId, Long gradeId) {
        // TODO: 实现年级成绩统计
        // 需要查询该年级下所有班级的成绩，进行汇总分析
        return GradeStatResponse.builder()
                .gradeId(gradeId)
                .examId(examId)
                .classComparisons(new ArrayList<>())
                .subjectStats(new ArrayList<>())
                .build();
    }

    @Override
    public SubjectStatResponse getSubjectStat(Long examId, Long subjectId) {
        List<Score> scores = scoreMapper.selectByExamId(examId, null).stream()
                .filter(s -> s.getSubjectId().equals(subjectId))
                .collect(Collectors.toList());

        List<Score> validScores = scores.stream()
                .filter(s -> s.getIsAbsent() == 0 && s.getScore() != null)
                .collect(Collectors.toList());

        int totalStudents = validScores.size();
        if (totalStudents == 0) {
            return SubjectStatResponse.builder()
                    .examId(examId).subjectId(subjectId)
                    .totalStudents(0)
                    .classAverages(new ArrayList<>())
                    .scoreRanges(new ArrayList<>())
                    .build();
        }

        BigDecimal sum = validScores.stream().map(Score::getScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avg = sum.divide(BigDecimal.valueOf(totalStudents), 2, RoundingMode.HALF_UP);
        BigDecimal max = validScores.stream().map(Score::getScore).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal min = validScores.stream().map(Score::getScore).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);

        // 标准差
        BigDecimal variance = validScores.stream().map(Score::getScore)
                .map(s -> s.subtract(avg).pow(2))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(totalStudents), 4, RoundingMode.HALF_UP);
        BigDecimal stdDev = BigDecimal.valueOf(Math.sqrt(variance.doubleValue()))
                .setScale(2, RoundingMode.HALF_UP);

        long passCount = validScores.stream().filter(s -> s.getScore().compareTo(BigDecimal.valueOf(60)) >= 0).count();
        long excellentCount = validScores.stream().filter(s -> s.getScore().compareTo(BigDecimal.valueOf(85)) >= 0).count();
        BigDecimal passRate = BigDecimal.valueOf(passCount * 100).divide(BigDecimal.valueOf(totalStudents), 2, RoundingMode.HALF_UP);
        BigDecimal excellentRate = BigDecimal.valueOf(excellentCount * 100).divide(BigDecimal.valueOf(totalStudents), 2, RoundingMode.HALF_UP);

        List<ClassStatResponse.ScoreRange> scoreRanges = calculateScoreRanges(
                validScores.stream().map(Score::getScore).collect(Collectors.toList()));

        // 各班平均分
        Map<Long, List<Score>> byClass = validScores.stream()
                .collect(Collectors.groupingBy(Score::getClassId));
        List<SubjectStatResponse.ClassAvg> classAvgs = byClass.entrySet().stream()
                .map(e -> {
                    BigDecimal classSum = e.getValue().stream().map(Score::getScore).reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal classAvg = classSum.divide(BigDecimal.valueOf(e.getValue().size()), 2, RoundingMode.HALF_UP);
                    return SubjectStatResponse.ClassAvg.builder()
                            .classId(e.getKey()).averageScore(classAvg).build();
                }).collect(Collectors.toList());

        return SubjectStatResponse.builder()
                .examId(examId).subjectId(subjectId)
                .totalStudents(totalStudents)
                .averageScore(avg).maxScore(max).minScore(min).stdDev(stdDev)
                .passCount((int) passCount).passRate(passRate)
                .excellentCount((int) excellentCount).excellentRate(excellentRate)
                .classAverages(classAvgs)
                .scoreRanges(scoreRanges)
                .build();
    }

    /**
     * 计算分数段分布
     */
    private List<ClassStatResponse.ScoreRange> calculateScoreRanges(List<BigDecimal> scores) {
        Map<String, Integer> rangeMap = new LinkedHashMap<>();
        rangeMap.put("0-60", 0);
        rangeMap.put("60-70", 0);
        rangeMap.put("70-80", 0);
        rangeMap.put("80-90", 0);
        rangeMap.put("90-100", 0);

        for (BigDecimal score : scores) {
            int scoreInt = score.intValue();
            if (scoreInt < 60) {
                rangeMap.put("0-60", rangeMap.get("0-60") + 1);
            } else if (scoreInt < 70) {
                rangeMap.put("60-70", rangeMap.get("60-70") + 1);
            } else if (scoreInt < 80) {
                rangeMap.put("70-80", rangeMap.get("70-80") + 1);
            } else if (scoreInt < 90) {
                rangeMap.put("80-90", rangeMap.get("80-90") + 1);
            } else {
                rangeMap.put("90-100", rangeMap.get("90-100") + 1);
            }
        }

        int total = scores.size();
        return rangeMap.entrySet().stream()
                .map(entry -> {
                    BigDecimal percentage = total > 0 ?
                            BigDecimal.valueOf(entry.getValue()).multiply(BigDecimal.valueOf(100))
                                    .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP) :
                            BigDecimal.ZERO;

                    return ClassStatResponse.ScoreRange.builder()
                            .range(entry.getKey())
                            .count(entry.getValue())
                            .percentage(percentage)
                            .build();
                })
                .collect(Collectors.toList());
    }

}
