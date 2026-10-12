package com.school.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.task.ThreadPoolTaskExecutorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 定时任务线程池配置
 * <p>
 * 不引入 Quartz：任务定义在 sys_job，由 JobScheduler 用这里的 TaskScheduler + CronTrigger 动态注册。
 *
 * @author School System
 */
@Configuration
@EnableScheduling
@Slf4j
public class SchedulingConfig {

    private static final int SCHEDULER_POOL_SIZE = 4;
    private static final int AWAIT_TERMINATION_SECONDS = 30;

    /**
     * 保留 Spring Boot 默认的 applicationTaskExecutor
     * <p>
     * ponytail: Boot 的 TaskExecutorConfiguration 是 @ConditionalOnMissingBean(Executor.class)，
     * 下面的 ThreadPoolTaskScheduler 也是 Executor，一旦声明就会让自动配置退场——
     * Spring MVC 的异步支持（ChatController 的 SSE Flux）会退回 SimpleAsyncTaskExecutor 并刷警告。
     * 这里按 Boot 原样用 ThreadPoolTaskExecutorBuilder 重新声明同名 Bean（含 taskExecutor 别名供 @EnableAsync 回退查找），行为不变。
     */
    @Bean(name = {"applicationTaskExecutor", "taskExecutor"})
    public ThreadPoolTaskExecutor applicationTaskExecutor(ThreadPoolTaskExecutorBuilder builder) {
        return builder.build();
    }

    /**
     * 定时任务调度线程池
     * <p>
     * 池大小 4：内置任务只有三个且都是低频 I/O 型，留一条给「立即执行」；
     * 关闭时等待在跑的任务完成（最多 30 秒），避免备份/清理做到一半被掐断。
     */
    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(SCHEDULER_POOL_SIZE);
        scheduler.setThreadNamePrefix("sys-job-");
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(AWAIT_TERMINATION_SECONDS);
        // ponytail: JobScheduler.execute 自己兜住 Exception；这里只接 Error 等漏网的 Throwable，不让 CronTrigger 链断掉
        scheduler.setErrorHandler(t -> log.error("定时任务线程未捕获异常", t));
        return scheduler;
    }

}
