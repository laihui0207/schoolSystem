package com.school.module.warning.service;

import com.school.module.score.entity.Score;
import com.school.module.score.mapper.ScoreMapper;
import com.school.module.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 成绩预警服务实现类
 *
 * @author School System
 */
@Service
@RequiredArgsConstructor
public class ScoreWarningServiceImpl implements ScoreWarningService {

    private final ScoreMapper scoreMapper;
    private final NotificationService notificationService;

    // 默认预警阈值：成绩下降超过20分
    private static final BigDecimal DEFAULT_DROP_THRESHOLD = BigDecimal.valueOf(20);

    // 不及格预警阈值：60分
    private static final BigDecimal PASS_THRESHOLD = BigDecimal.valueOf(60);

    @Override
    public void checkScoreWarnings(Long examId) {
        // 查询该考试的所有成绩
        List<Score> currentScores = scoreMapper.selectByExamId(examId, null);

        if (currentScores.isEmpty()) {
            return;
        }

        // 按学生分组
        Map<Long, List<Score>> studentScoresMap = currentScores.stream()
                .collect(Collectors.groupingBy(Score::getStudentId));

        List<Long> warningStudents = new ArrayList<>();

        for (Map.Entry<Long, List<Score>> entry : studentScoresMap.entrySet()) {
            Long studentId = entry.getKey();
            List<Score> scores = entry.getValue();

            // 检查是否有不及格科目
            boolean hasFailedSubject = scores.stream()
                    .anyMatch(s -> s.getScore() != null &&
                                 s.getScore().compareTo(PASS_THRESHOLD) < 0);

            if (hasFailedSubject) {
                warningStudents.add(studentId);
                continue;
            }

            // TODO: 检查成绩下降（需要查询历史成绩对比）
            // 这里简化处理，实际应该对比上次考试成绩
        }

        // 发送预警通知
        if (!warningStudents.isEmpty()) {
            String title = "成绩预警通知";
            String content = "您有科目成绩不及格或成绩大幅下降，请及时关注学习情况。";
            notificationService.batchCreateNotifications(
                    warningStudents, title, content, 2, examId);
        }
    }

    @Override
    public void checkScoreDropWarning(Long studentId, Long examId, Long subjectId) {
        // TODO: 实现单个学生成绩下降预警
        // 需要查询学生历史成绩，对比分析
    }

    @Override
    public Map<String, Object> analyzeScoreTrend(Long studentId, Long subjectId) {
        // TODO: 实现成绩趋势分析
        Map<String, Object> result = new HashMap<>();
        result.put("trend", "stable"); // stable/rising/falling
        result.put("warningLevel", 0); // 0-正常，1-注意，2-预警
        return result;
    }

}
