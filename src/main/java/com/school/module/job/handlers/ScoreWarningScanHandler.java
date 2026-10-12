package com.school.module.job.handlers;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.school.common.constant.ExamStatusConstants;
import com.school.module.exam.entity.Exam;
import com.school.module.exam.mapper.ExamMapper;
import com.school.module.job.spi.JobHandler;
import com.school.module.job.spi.JobParams;
import com.school.module.warning.service.ScoreWarningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 内置任务：成绩预警补扫
 * <p>
 * params.days（默认 7）：对 exam_date 在「今天 - days」及之后、状态为已发布的考试逐个调用
 * {@link ScoreWarningService#checkScoreWarnings(Long)}。该方法自带「同一考试已发过预警的学生不重发」去重，
 * 所以每天重复扫描是安全的；它的作用是兜住发布当时因阈值配置、通知写入失败等原因漏发的预警。
 * <p>
 * ponytail: 调度线程装的是 userType=1 的系统身份，租户插件对 exam 表放行，一次扫全平台；
 * 单场考试失败只计数，不影响其余考试。
 *
 * @author School System
 */
@Component(ScoreWarningScanHandler.BEAN_NAME)
@RequiredArgsConstructor
@Slf4j
public class ScoreWarningScanHandler implements JobHandler {

    public static final String BEAN_NAME = "scoreWarningScanHandler";
    public static final String PARAM_DAYS = "days";
    static final int DEFAULT_DAYS = 7;

    private final ExamMapper examMapper;
    private final ScoreWarningService scoreWarningService;

    @Override
    public String handle(Map<String, Object> params) {
        int days = JobParams.intValue(params, PARAM_DAYS, DEFAULT_DAYS);
        if (days < 1) {
            throw new IllegalArgumentException(PARAM_DAYS + " 必须是正整数，当前值: " + days);
        }
        LocalDate since = LocalDate.now().minusDays(days);
        List<Exam> exams = examMapper.selectList(new LambdaQueryWrapper<Exam>()
                .eq(Exam::getStatus, ExamStatusConstants.PUBLISHED)
                .ge(Exam::getExamDate, since)
                .orderByAsc(Exam::getExamDate, Exam::getId));

        int success = 0;
        List<String> failures = new ArrayList<>();
        for (Exam exam : exams) {
            try {
                scoreWarningService.checkScoreWarnings(exam.getId());
                success++;
            } catch (Exception e) {
                log.warn("考试 {} 预警扫描失败: {}", exam.getId(), e.getMessage());
                failures.add(exam.getId() + ":" + e.getMessage());
            }
        }
        String summary = String.format("扫描 %s 起已发布考试 %d 场，成功 %d，失败 %d",
                since, exams.size(), success, failures.size());
        if (!failures.isEmpty()) {
            summary += "；失败明细: " + String.join("; ", failures);
        }
        log.info("成绩预警扫描完成：{}", summary);
        return summary;
    }

}
