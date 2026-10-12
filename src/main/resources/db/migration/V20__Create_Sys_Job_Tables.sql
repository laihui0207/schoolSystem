-- V20: 定时任务（sys_job）与执行日志（sys_job_log）—— 计划 2.1 系统管理：定时任务管理 / 3.3 异步统计
-- 不引入 Quartz：任务定义落库，启动时由 JobScheduler 用 Spring TaskScheduler + CronTrigger 注册。
-- cron 为 Spring 6 位格式（秒 分 时 日 月 周），由 CronExpression.isValidExpression 校验。
-- 全部 IF NOT EXISTS / WHERE NOT EXISTS 幂等。

CREATE TABLE IF NOT EXISTS sys_job (
    id              BIGSERIAL PRIMARY KEY,
    job_name        VARCHAR(100) NOT NULL,
    job_code        VARCHAR(50)  NOT NULL,
    handler_bean    VARCHAR(100) NOT NULL,
    cron            VARCHAR(50)  NOT NULL,
    params          TEXT,
    status          SMALLINT     NOT NULL DEFAULT 0,
    last_run_at     TIMESTAMP,
    last_run_status SMALLINT,
    next_run_at     TIMESTAMP,
    remark          VARCHAR(500),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT  DEFAULT 0
);

-- job_code 对未删除行唯一：用部分唯一索引而不是列级 UNIQUE，软删后同名任务还能重建（同 V14 exam_subject 的做法）
CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_job_code ON sys_job(job_code) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_sys_job_status ON sys_job(status);

COMMENT ON TABLE sys_job IS '定时任务定义';
COMMENT ON COLUMN sys_job.job_code IS '任务编码，调度器 Map 的 key，未删除行内唯一';
COMMENT ON COLUMN sys_job.handler_bean IS '执行器 Spring Bean 名，须实现 com.school.module.job.spi.JobHandler';
COMMENT ON COLUMN sys_job.cron IS 'Spring 6 位 cron（秒 分 时 日 月 周）';
COMMENT ON COLUMN sys_job.params IS '传给 handler 的 JSON 对象，如 {"retainDays":90}';
COMMENT ON COLUMN sys_job.status IS '0-停用，1-启用';
COMMENT ON COLUMN sys_job.last_run_status IS '最近一次执行结果：1-成功，0-失败';
COMMENT ON COLUMN sys_job.next_run_at IS '按 cron 计算的下次触发时间，停用时为空';

CREATE TABLE IF NOT EXISTS sys_job_log (
    id           BIGSERIAL PRIMARY KEY,
    job_id       BIGINT      NOT NULL,
    job_code     VARCHAR(50) NOT NULL,
    started_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at  TIMESTAMP,
    status       SMALLINT    NOT NULL DEFAULT 2,
    message      TEXT,
    duration_ms  BIGINT,
    triggered_by VARCHAR(50)
);

CREATE INDEX IF NOT EXISTS idx_sys_job_log_job_started ON sys_job_log(job_id, started_at DESC);

COMMENT ON TABLE sys_job_log IS '定时任务执行日志（只追加，无软删除）';
COMMENT ON COLUMN sys_job_log.status IS '1-成功，0-失败，2-执行中（立即执行先插行再异步跑，接口据此返回 jobLogId）';
COMMENT ON COLUMN sys_job_log.message IS 'handler 返回的摘要或异常消息，截 2000';
COMMENT ON COLUMN sys_job_log.triggered_by IS 'cron 表示调度触发；否则为手动触发者用户名';

-- 预置 3 条任务，均为停用状态，由超管在「定时任务」页按需开启
INSERT INTO sys_job (job_name, job_code, handler_bean, cron, params, status, remark)
SELECT '清理操作日志', 'cleanOperationLogs', 'cleanOperationLogsHandler', '0 0 3 * * ?', '{"retainDays":90}', 0,
       '每天 03:00 删除 retainDays 天之前的 sys_operation_log'
WHERE NOT EXISTS (SELECT 1 FROM sys_job WHERE job_code = 'cleanOperationLogs');

INSERT INTO sys_job (job_name, job_code, handler_bean, cron, params, status, remark)
SELECT '成绩预警扫描', 'scoreWarningScan', 'scoreWarningScanHandler', '0 0 6 * * ?', '{"days":7}', 0,
       '每天 06:00 对近 days 天内已发布的考试补发成绩预警（已发过的学生不重发）'
WHERE NOT EXISTS (SELECT 1 FROM sys_job WHERE job_code = 'scoreWarningScan');

INSERT INTO sys_job (job_name, job_code, handler_bean, cron, params, status, remark)
SELECT '数据库备份', 'backupDatabase', 'backupJobHandler', '0 0 2 * * ?', NULL, 0,
       '每天 02:00 执行数据库备份（handler 由备份模块提供，缺失时记失败日志）'
WHERE NOT EXISTS (SELECT 1 FROM sys_job WHERE job_code = 'backupDatabase');
