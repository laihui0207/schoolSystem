package com.school.module.backup.service.strategy;

import com.school.module.backup.config.BackupProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按 app.backup.mode 选择备份策略
 * <p>
 * auto：依次探测 pg_dump → docker → sql，取第一个可用的；sql 恒可用所以 auto 永远有结果。
 * 显式指定某一模式时不探测，直接使用，让失败原因（如「找不到 pg_dump」）原样写进记录。
 *
 * @author School System
 */
@Slf4j
@Component
public class BackupStrategyResolver {

    /** auto 模式的探测顺序 */
    static final List<String> AUTO_ORDER = List.of(
            BackupProperties.MODE_PG_DUMP, BackupProperties.MODE_DOCKER, BackupProperties.MODE_SQL);

    private final Map<String, BackupStrategy> strategies = new LinkedHashMap<>();

    public BackupStrategyResolver(List<BackupStrategy> available) {
        for (BackupStrategy s : available) {
            strategies.put(s.mode(), s);
        }
    }

    /**
     * @param mode auto | pg_dump | docker | sql（null/空按 auto）
     * @throws IllegalStateException 未知模式，或显式模式对应的策略未注册
     */
    public BackupStrategy resolve(String mode) {
        String wanted = mode == null || mode.isBlank() ? BackupProperties.MODE_AUTO : mode.trim().toLowerCase();
        if (BackupProperties.MODE_AUTO.equals(wanted)) {
            return resolveAuto();
        }
        BackupStrategy strategy = strategies.get(wanted);
        if (strategy == null) {
            throw new IllegalStateException("未知的备份模式 app.backup.mode=" + mode
                    + "，可选: auto / pg_dump / docker / sql");
        }
        return strategy;
    }

    private BackupStrategy resolveAuto() {
        for (String mode : AUTO_ORDER) {
            BackupStrategy strategy = strategies.get(mode);
            if (strategy != null && strategy.available()) {
                log.info("备份策略 auto 探测结果: {}", mode);
                return strategy;
            }
        }
        throw new IllegalStateException("没有任何可用的备份策略（sql 策略未注册）");
    }

}
