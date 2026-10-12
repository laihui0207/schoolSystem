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
 * 本机 pg_dump 策略：{@code pg_dump -Fc -h host -p port -U user -d db -f file}
 * <p>
 * 产物是 custom 格式（.dump），需用 pg_restore 恢复，本应用的在线恢复接口不支持它。
 *
 * @author School System
 */
@Component
@RequiredArgsConstructor
public class PgDumpBackupStrategy implements BackupStrategy {

    public static final String FILE_EXTENSION = ".dump";

    private final BackupProperties properties;
    private final BackupDataSourceInfo dataSourceInfo;

    @Override
    public String mode() {
        return BackupProperties.MODE_PG_DUMP;
    }

    @Override
    public String fileExtension() {
        return FILE_EXTENSION;
    }

    @Override
    public boolean available() {
        return ExternalProcessSupport.probe(List.of(properties.getPgDumpPath(), "--version"));
    }

    @Override
    public String dump(Path target) throws IOException, InterruptedException {
        List<String> command = List.of(
                properties.getPgDumpPath(), "-Fc",
                "-h", dataSourceInfo.getHost(),
                "-p", String.valueOf(dataSourceInfo.getPort()),
                "-U", dataSourceInfo.getUsername(),
                "-d", dataSourceInfo.getDatabase(),
                "-f", target.toString());
        // ponytail: PGPASSWORD 走环境变量，不进命令行
        Map<String, String> env = Map.of("PGPASSWORD", dataSourceInfo.getPassword() == null ? "" : dataSourceInfo.getPassword());
        ExternalProcessSupport.ProcessResult result = ExternalProcessSupport.run(
                command, env, null, Duration.ofSeconds(properties.getProcessTimeoutSeconds()));
        if (!result.ok()) {
            throw new IOException("pg_dump 退出码 " + result.exitCode() + ": " + result.stderr());
        }
        return "pg_dump -Fc";
    }

}
