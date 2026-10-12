package com.school.module.backup.service.strategy;

import java.nio.file.Path;

/**
 * 备份策略：把当前数据库导出到一个文件
 * <p>
 * 三种实现：本机 pg_dump、docker exec 容器内 pg_dump、纯 JDBC 生成 SQL。
 * {@link BackupStrategyResolver} 按 app.backup.mode 选择，auto 时按 pg_dump → docker → sql 顺序探测。
 *
 * @author School System
 */
public interface BackupStrategy {

    /**
     * 策略标识，写入 sys_backup.backup_mode（pg_dump / docker / sql）
     */
    String mode();

    /**
     * 产物文件扩展名（含点），如 ".dump" / ".sql.gz"
     */
    String fileExtension();

    /**
     * 当前环境是否可用（探测外部命令是否存在）；sql 策略恒为 true
     */
    boolean available();

    /**
     * 执行导出
     *
     * @param target 目标文件（父目录已存在）
     * @return 可读的摘要（如 "23 张表, 745120 行"），可为 null
     * @throws Exception 任何失败；调用方负责删除半成品文件并记录 status=2
     */
    String dump(Path target) throws Exception;

}
