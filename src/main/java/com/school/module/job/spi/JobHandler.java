package com.school.module.job.spi;

import java.util.Map;

/**
 * 定时任务执行器 SPI
 * <p>
 * 实现类注册为 Spring Bean，{@code sys_job.handler_bean} 存 Bean 名，调度时由
 * {@code JobScheduler} 通过 {@code applicationContext.getBean(name, JobHandler.class)} 取出执行。
 * <p>
 * 执行线程已装入 userType=1 的系统身份（userId=0, username="system"），
 * 实现类内部可以直接调用依赖 {@code SecurityUtils} 的 Service；租户插件对该身份放行，查询不限学校。
 * <p>
 * 约定：
 * <ul>
 *   <li>返回值是一句人类可读的执行摘要（如「删除 120 条」），会原样写入 sys_job_log.message；</li>
 *   <li>抛出任何异常视为执行失败，调度器记失败日志但不中断后续调度；</li>
 *   <li>params 是 sys_job.params 解析出的 JSON 对象，可能为空 Map，不要改它。</li>
 * </ul>
 *
 * @author School System
 */
public interface JobHandler {

    /**
     * 执行任务
     *
     * @param params sys_job.params 解析后的键值（只读，可能为空）
     * @return 执行摘要，写入 sys_job_log.message
     */
    String handle(Map<String, Object> params);

}
