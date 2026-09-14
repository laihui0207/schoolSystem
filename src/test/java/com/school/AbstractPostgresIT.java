package com.school;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 集成测试基类：用 Testcontainers 起一个真 Postgres。
 *
 * <p>迁移脚本是 PostgreSQL 方言（例如 V3 的表达式唯一索引 COALESCE(...)），
 * H2 即使开 PostgreSQL 兼容模式也无法执行；而直接改迁移会破坏已应用到真库的
 * Flyway checksum。因此 @SpringBootTest 的上下文测试统一跑在真 PG 上，脚本原样执行。
 *
 * <p>镜像与 docker-compose 保持一致（postgres:16-alpine），本地已缓存，无需额外拉取。
 */
@Testcontainers
public abstract class AbstractPostgresIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }
}
