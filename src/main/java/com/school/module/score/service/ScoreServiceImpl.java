package com.school.module.score.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.common.exception.BusinessException;
import com.school.module.score.dto.*;
import com.school.module.score.entity.Score;
import com.school.module.score.mapper.ScoreMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 成绩服务实现类
 *
 * @author School System
 */
@Service
@RequiredArgsConstructor
public class ScoreServiceImpl implements ScoreService {

    private final ScoreMapper scoreMapper;

    private static final List<Integer> VALID_STATUS = Arrays.asList(0, 1, 2);

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScoreResponse inputScore(ScoreInputRequest request) {
        // 校验是否已存在成绩
        Score existingScore = scoreMapper.selectByExamStudentSubject(
                request.getExamId(),
                request.getStudentId(),
                request.getSubjectId()
        );

        if (existingScore != null) {
            throw new BusinessException("该学生的成绩已存在");
        }

        // 校验缺考和分数
        if (request.getIsAbsent() != null && request.getIsAbsent() == 1) {
            // 缺考时分数应为空
            request.setScore(null);
        } else if (request.getScore() == null) {
            throw new BusinessException("未缺考时必须填写分数");
        }

        // 构建成绩实体
        Score score = new Score();
        score.setExamId(request.getExamId());
        score.setStudentId(request.getStudentId());
        score.setSubjectId(request.getSubjectId());
        score.setClassId(request.getClassId());
        score.setSchoolId(request.getSchoolId());
        score.setScore(request.getScore());
        score.setIsAbsent(request.getIsAbsent() != null ? request.getIsAbsent() : 0);
        score.setStatus(0); // 默认待审核
        score.setRecordedAt(LocalDateTime.now());

        // 保存成绩
        scoreMapper.insert(score);

        return convertToResponse(score);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer batchInputScores(ScoreBatchInputRequest request) {
        int successCount = 0;

        for (ScoreBatchInputRequest.ScoreItem item : request.getScores()) {
            try {
                // 检查是否已存在
                Score existingScore = scoreMapper.selectByExamStudentSubject(
                        request.getExamId(),
                        item.getStudentId(),
                        item.getSubjectId()
                );

                if (existingScore != null) {
                    continue; // 跳过已存在的成绩
                }

                // 构建成绩实体
                Score score = new Score();
                score.setExamId(request.getExamId());
                score.setStudentId(item.getStudentId());
                score.setSubjectId(item.getSubjectId());
                score.setClassId(item.getClassId());
                score.setSchoolId(item.getSchoolId());
                score.setScore(item.getScore());
                score.setIsAbsent(item.getIsAbsent() != null ? item.getIsAbsent() : 0);
                score.setStatus(0);
                score.setRecordedAt(LocalDateTime.now());

                scoreMapper.insert(score);
                successCount++;
            } catch (Exception e) {
                // 记录日志，继续处理下一条
                continue;
            }
        }

        return successCount;
    }

    @Override
    public ScoreResponse getScoreById(Long id) {
        Score score = scoreMapper.selectById(id);
        if (score == null) {
            throw new BusinessException("成绩不存在");
        }
        return convertToResponse(score);
    }

    @Override
    public Page<ScoreResponse> listScores(ScoreQueryRequest request) {
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

        List<ScoreResponse> responses = resultPage.getRecords().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());

        responsePage.setRecords(responses);

        return responsePage;
    }

    @Override
    public List<ScoreResponse> getStudentExamScores(Long examId, Long studentId) {
        ScoreQueryRequest request = ScoreQueryRequest.builder()
                .examId(examId)
                .studentId(studentId)
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

        for (Long scoreId : request.getScoreIds()) {
            Score score = scoreMapper.selectById(scoreId);
            if (score == null) {
                continue;
            }

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

            scoreMapper.updateById(updatedScore);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void calculateRankings(Long examId, Long subjectId) {
        // 查询该考试的所有成绩
        List<Score> scores = scoreMapper.selectByExamId(examId, null);

        if (scores.isEmpty()) {
            return;
        }

        // TODO: 实现排名计算逻辑
        // 1. 按班级、年级、学校分组
        // 2. 按分数排序
        // 3. 更新排名字段
        // 这里先留空，后续完善
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteScore(Long id) {
        Score score = scoreMapper.selectById(id);
        if (score == null) {
            throw new BusinessException("成绩不存在");
        }

        // 只能删除待审核或已驳回的成绩
        if (score.getStatus() == 1) {
            throw new BusinessException("已审核通过的成绩不能删除");
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
                .recorderId(score.getRecorderId())
                .status(score.getStatus())
                .statusName(getStatusName(score.getStatus()))
                .recordedAt(score.getRecordedAt())
                .reviewedAt(score.getReviewedAt())
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

    @Override
    public List<ClassScoreItemDto> getClassScoreList(Long examId, Long classId, Long subjectId) {
        List<Map<String, Object>> rows = scoreMapper.selectClassScoreItems(examId, classId, subjectId);
        return rows.stream().map(r -> {
            ClassScoreItemDto dto = new ClassScoreItemDto();
            dto.setId(toLong(r.get("id")));
            dto.setStudentId(toLong(r.get("student_id")));
            dto.setStudentNo((String) r.get("studentNo"));
            dto.setStudentName((String) r.get("studentName"));
            dto.setScore(r.get("score") != null ? new BigDecimal(r.get("score").toString()) : null);
            dto.setIsAbsent(Integer.valueOf(1).equals(r.get("is_absent")));
            return dto;
        }).collect(Collectors.toList());
    }

    @Override
    public ScoreStatisticsDto getScoreStatistics(Long examId, Long classId, Long subjectId) {
        List<Score> scores = scoreMapper.selectByExamId(examId, classId).stream()
                .filter(s -> subjectId == null || subjectId.equals(s.getSubjectId()))
                .collect(Collectors.toList());
        List<Score> valid = scores.stream().filter(s -> s.getIsAbsent() == 0 && s.getScore() != null)
                .collect(Collectors.toList());
        int total = scores.size();
        int inputCount = valid.size();
        double avg = inputCount > 0 ? valid.stream().mapToDouble(s -> s.getScore().doubleValue()).average().orElse(0) : 0;
        double max = valid.stream().mapToDouble(s -> s.getScore().doubleValue()).max().orElse(0);
        double min = valid.stream().mapToDouble(s -> s.getScore().doubleValue()).min().orElse(0);
        long passCount = valid.stream().filter(s -> s.getScore().doubleValue() >= 60).count();
        long exCount = valid.stream().filter(s -> s.getScore().doubleValue() >= 85).count();
        ScoreStatisticsDto dto = new ScoreStatisticsDto();
        dto.setTotalCount(total);
        dto.setEnteredCount(inputCount);
        dto.setAverageScore(avg);
        dto.setHighestScore(max);
        dto.setLowestScore(min);
        dto.setPassRate(inputCount > 0 ? (double) passCount * 100 / inputCount : 0);
        dto.setExcellentRate(inputCount > 0 ? (double) exCount * 100 / inputCount : 0);
        return dto;
    }

    @Override
    public List<ReviewListItemDto> getReviewList(Long examId, Long classId, Long subjectId, Long schoolId) {
        List<Map<String, Object>> rows = scoreMapper.selectReviewList(examId, classId, subjectId, schoolId);
        return rows.stream().map(r -> {
            ReviewListItemDto dto = new ReviewListItemDto();
            dto.setExamId(toLong(r.get("examId")));
            dto.setExamName((String) r.get("examName"));
            dto.setClassId(toLong(r.get("classId")));
            dto.setClassName((String) r.get("className"));
            dto.setSubjectId(toLong(r.get("subjectId")));
            dto.setSubjectName((String) r.get("subjectName"));
            Object fs = r.get("fullScore");
            dto.setFullScore(fs != null ? Double.parseDouble(fs.toString()) : 100.0);
            Integer st = r.get("status") != null ? ((Number) r.get("status")).intValue() : 0;
            dto.setStatus(st == 1 ? "approved" : st == 2 ? "rejected" : "pending");
            return dto;
        }).collect(Collectors.toList());
    }

    @Override
    public ReviewDetailDto getReviewDetail(Long examId, Long classId, Long subjectId) {
        ReviewDetailDto dto = new ReviewDetailDto();
        // scores
        List<ClassScoreItemDto> scores = getClassScoreList(examId, classId, subjectId);
        dto.setScores(scores);
        // statistics
        ScoreStatisticsDto stats = getScoreStatistics(examId, classId, subjectId);
        ReviewDetailDto.Statistics s = new ReviewDetailDto.Statistics();
        s.setTotalCount(stats.getTotalCount());
        s.setEnteredCount(stats.getEnteredCount());
        s.setAverageScore(stats.getAverageScore());
        s.setHighestScore(stats.getHighestScore());
        s.setLowestScore(stats.getLowestScore());
        s.setPassRate(stats.getPassRate());
        s.setExcellentRate(stats.getExcellentRate());
        dto.setStatistics(s);
        // status from first score
        List<Score> raw = scoreMapper.selectByExamId(examId, classId).stream()
                .filter(sc -> subjectId == null || subjectId.equals(sc.getSubjectId()))
                .collect(Collectors.toList());
        if (!raw.isEmpty()) {
            int st = raw.get(0).getStatus();
            dto.setStatus(st == 1 ? "approved" : st == 2 ? "rejected" : "pending");
        }
        return dto;
    }

    @Override
    public List<ReviewHistoryDto> getReviewHistory(Long examId, Long classId, Long subjectId) {
        // ponytail: score_change_log table tracks review operations; return empty list if no log
        return List.of();
    }

    @Override
    public List<ScoreDistributionDto> getScoreDistribution(Long examId, Long classId, Long subjectId) {
        List<Score> scores = scoreMapper.selectByExamId(examId, classId).stream()
                .filter(s -> subjectId == null || subjectId.equals(s.getSubjectId()))
                .filter(s -> s.getIsAbsent() == 0 && s.getScore() != null)
                .collect(Collectors.toList());
        int[] ranges = {60, 70, 80, 90, 101};
        String[] labels = {"0-59", "60-69", "70-79", "80-89", "90-100"};
        int[] counts = new int[5];
        for (Score sc : scores) {
            double v = sc.getScore().doubleValue();
            if (v < 60) counts[0]++;
            else if (v < 70) counts[1]++;
            else if (v < 80) counts[2]++;
            else if (v < 90) counts[3]++;
            else counts[4]++;
        }
        List<ScoreDistributionDto> result = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) result.add(new ScoreDistributionDto(labels[i], counts[i]));
        return result;
    }

    private Long toLong(Object v) { return v == null ? null : ((Number) v).longValue(); }
    private static final java.math.BigDecimal BD_ZERO = java.math.BigDecimal.ZERO;

}
