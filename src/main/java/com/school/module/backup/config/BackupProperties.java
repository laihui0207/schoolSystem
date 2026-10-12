package com.school.module.backup.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 数据备份配置（application.yml 的 app.backup.* 段）
 *
 * @author School System
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.backup")
public class BackupProperties {

    public static final String MODE_AUTO = "auto";
    public static final String MODE_PG_DUMP = "pg_dump";
    public static final String MODE_DOCKER = "docker";
    public static final String MODE_SQL = "sql";

    /**
     * 备份文件目录（相对路径基于进程工作目录）
     */
    // ponytail: 默认值与 application.yml 保持一致，yml 缺省该段时仍可工作
    private String dir = "./backups";

    /**
     * 保留的成功备份数量，超出部分按最旧删除（文件 + 记录）
     */
    private int keepCount = 30;

    /**
     * 备份策略：auto（依次探测 pg_dump → docker → sql）| pg_dump | docker | sql
     */
    private String mode = MODE_AUTO;

    /**
     * docker 模式下运行 PostgreSQL 的容器名
     */
    private String dockerContainer = "school-postgres";

    /**
     * pg_dump 可执行文件路径（不在 PATH 上时填绝对路径）
     */
    private String pgDumpPath = "pg_dump";

    /**
     * 外部进程（pg_dump / docker exec）最长运行时间，秒
     */
    private long processTimeoutSeconds = 1800;

}
