-- V21: 数据备份记录表（计划 2.1 系统管理：数据备份与恢复）
-- 每次备份（手动 / 定时 / 恢复前自动 / 上传导入）一行；文件落在 app.backup.dir，表里只存元数据。
-- 全部 IF NOT EXISTS 幂等。
CREATE TABLE IF NOT EXISTS sys_backup (
    id            BIGSERIAL PRIMARY KEY,
    file_name     VARCHAR(255) NOT NULL,
    file_path     VARCHAR(500),
    file_size     BIGINT,
    backup_type   SMALLINT NOT NULL DEFAULT 1,
    backup_mode   VARCHAR(20),
    status        SMALLINT NOT NULL DEFAULT 0,
    message       TEXT,
    triggered_by  VARCHAR(50),
    started_at    TIMESTAMP,
    finished_at   TIMESTAMP,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted       SMALLINT DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_sys_backup_status_created ON sys_backup(status, created_at DESC);

COMMENT ON TABLE sys_backup IS '数据备份记录';
COMMENT ON COLUMN sys_backup.file_name IS '备份文件名（.dump = pg_dump -Fc 产物；.sql.gz = 本应用 JDBC 生成，可在线恢复）';
COMMENT ON COLUMN sys_backup.file_path IS '文件绝对路径';
COMMENT ON COLUMN sys_backup.file_size IS '文件字节数';
COMMENT ON COLUMN sys_backup.backup_type IS '1-手动（含上传/恢复前自动），2-定时任务';
COMMENT ON COLUMN sys_backup.backup_mode IS '实际使用的策略：pg_dump / docker / sql / upload';
COMMENT ON COLUMN sys_backup.status IS '0-进行中，1-成功，2-失败';
COMMENT ON COLUMN sys_backup.message IS '失败原因或备注（如「恢复前自动备份」）';
COMMENT ON COLUMN sys_backup.triggered_by IS '触发人：用户名 / job（定时任务）';
