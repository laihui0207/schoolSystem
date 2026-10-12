package com.school;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 学生成绩管理系统启动类
 *
 * @author School System
 * @since 2024-01-01
 */
@SpringBootApplication
@MapperScan("com.school.module.**.mapper")
@EnableCaching
@EnableAsync
public class SchoolSystemApplication {

    public static void main(String[] args) {
        // ponytail: InheritableThreadLocal 让 Netty/Reactor 子线程继承 Servlet 线程的 SecurityContext
        // 工具调用在 chatModel.stream() 内部的子线程中执行，必须能读到认证信息
        SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_INHERITABLETHREADLOCAL);
        SpringApplication.run(SchoolSystemApplication.class, args);
    }

}
