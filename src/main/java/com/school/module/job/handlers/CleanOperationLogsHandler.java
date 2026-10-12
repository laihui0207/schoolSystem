package com.school.module.job.handlers;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.school.module.job.spi.JobHandler;
import com.school.module.job.spi.JobParams;
import com.school.module.log.entity.OperationLog;
import com.school.module.log.mapper.OperationLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 内置任务：清理过期操作日志
 * <p>
 * params.retainDays（默认 90）：删除 sys_operation_log 中 created_at 早于「现在 - retainDays 天」的行。
 * ponytail: OperationLog 没有 @TableLogic，BaseMapper.delete 是物理 DELETE——日志表本就没有 deleted 列，
 * 软删只会让表继续膨胀，与「清理」目的相悖。只读引用 log 模块的 Mapper，不改它。
 *
 * @author School System
 */
@Component(CleanOperationLogsHandler.BEAN_NAME)
@RequiredArgsConstructor
@Slf4j
public class CleanOperationLogsHandler implements JobHandler {

    public static final String BEAN_NAME = "cleanOperationLogsHandler";
    public static final String PARAM_RETAIN_DAYS = "retainDays";
    static final int DEFAULT_RETAIN_DAYS = 90;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final OperationLogMapper operationLogMapper;

    @Override
    public String handle(Map<String, Object> params) {
        int retainDays = JobParams.intValue(params, PARAM_RETAIN_DAYS, DEFAULT_RETAIN_DAYS);
        // ponytail: retainDays<=0 等于清空全部审计记录，宁可任务失败也不执行
        if (retainDays < 1) {
            throw new IllegalArgumentException(PARAM_RETAIN_DAYS + " 必须是正整数，当前值: " + retainDays);
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retainDays);
        int deleted = operationLogMapper.delete(new LambdaQueryWrapper<OperationLog>()
                .lt(OperationLog::getCreatedAt, cutoff));
        String summary = String.format("保留 %d 天，删除 %s 之前的操作日志 %d 条",
                retainDays, cutoff.format(FORMATTER), deleted);
        log.info("清理操作日志完成：{}", summary);
        return summary;
    }

}
