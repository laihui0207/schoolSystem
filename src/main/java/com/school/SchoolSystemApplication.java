package com.school;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 学生成绩管理系统启动类
 *
 * @author School System
 * @since 2024-01-01
 */
@SpringBootApplication
@MapperScan("com.school.module.*.mapper")
@EnableCaching
@EnableAsync
public class SchoolSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(SchoolSystemApplication.class, args);
    }

}
