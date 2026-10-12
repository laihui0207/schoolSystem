package com.school.module.backup.service.strategy;

import com.school.module.backup.config.BackupDataSourceInfo;
import com.school.module.backup.config.BackupProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * docker 策略：{@code docker exec <container> pg_dump -Fc -U user -d db}，stdout 直接重定向到文件
 * <p>
 * 适用于应用机没有 pg_dump、但数据库跑在本机 Docker 容器里的部署（开发机就是这种）。
 * ponytail: {@code -e PGPASSWORD} 不带 "=值" 时 docker 会把 docker CLI 自身环境里的同名变量透传进容器，
 * 于是密码仍然只走环境变量、不出现在命令行。容器内 pg_dump 走本地 socket，不需要 -h。
 *
 * @author School System
 */
@Component
@RequiredArgsConstructor
public class DockerPgDumpBackupStrategy implements BackupStrategy {

    private final BackupProperties properties;
    private final BackupDataSourceInfo dataSourceInfo;

    @Override
    public String mode() {
        return BackupProperties.MODE_DOCKER;
    }

    @Override
    public String fileExtension() {
        return PgDumpBackupStrategy.FILE_EXTENSION;
    }

    @Override
    public boolean available() {
        return ExternalProcessSupport.probe(
                List.of("docker", "exec", properties.getDockerContainer(), "pg_dump", "--version"));
    }

    @Override
    public String dump(Path target) throws IOException, InterruptedException {
        List<String> command = List.of(
                "docker", "exec", "-e", "PGPASSWORD", properties.getDockerContainer(),
                "pg_dump", "-Fc",
                "-U", dataSourceInfo.getUsername(),
                "-d", dataSourceInfo.getDatabase());
        Map<String, String> env = Map.of("PGPASSWORD", dataSourceInfo.getPassword() == null ? "" : dataSourceInfo.getPassword());
        ExternalProcessSupport.ProcessResult result = ExternalProcessSupport.run(
                command, env, target, Duration.ofSeconds(properties.getProcessTimeoutSeconds()));
        if (!result.ok()) {
            throw new IOException("docker exec pg_dump 退出码 " + result.exitCode() + ": " + result.stderr());
        }
        return "docker exec " + properties.getDockerContainer() + " pg_dump -Fc";
    }

}
