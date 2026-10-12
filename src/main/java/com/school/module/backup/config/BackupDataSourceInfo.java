package com.school.module.backup.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * 从 spring.datasource.* 解析出 pg_dump 需要的 host / port / database / user / password
 * <p>
 * ponytail: 不另起一套 app.backup.db.* 配置——数据源只有一个，重复配置迟早漂移；
 * JDBC URL 形如 jdbc:postgresql://host:port/db?params，去掉 "jdbc:" 前缀后就是标准 URI。
 *
 * @author School System
 */
@Getter
@Component
public class BackupDataSourceInfo {

    private static final int DEFAULT_PG_PORT = 5432;

    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;

    public BackupDataSourceInfo(@Value("${spring.datasource.url}") String jdbcUrl,
                                @Value("${spring.datasource.username}") String username,
                                @Value("${spring.datasource.password:}") String password) {
        this.username = username;
        this.password = password;
        URI uri = parse(jdbcUrl);
        this.host = uri.getHost() == null ? "localhost" : uri.getHost();
        this.port = uri.getPort() > 0 ? uri.getPort() : DEFAULT_PG_PORT;
        String path = uri.getPath() == null ? "" : uri.getPath();
        this.database = path.startsWith("/") ? path.substring(1) : path;
        if (this.database.isBlank()) {
            throw new IllegalStateException("无法从 spring.datasource.url 解析数据库名: " + jdbcUrl);
        }
    }

    private static URI parse(String jdbcUrl) {
        if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:")) {
            throw new IllegalStateException("spring.datasource.url 不是 JDBC URL: " + jdbcUrl);
        }
        try {
            return new URI(jdbcUrl.substring("jdbc:".length()));
        } catch (URISyntaxException e) {
            throw new IllegalStateException("spring.datasource.url 解析失败: " + jdbcUrl, e);
        }
    }

}
