package com.school.module.config.service;

import java.math.BigDecimal;

/**
 * 成绩规则 / 系统开关读取服务（契约 K1）
 * <p>
 * 把 sys_config 中几个内置键解析成业务可直接使用的类型，缺失或非法时回落到默认值。
 * 消费方：StatServiceImpl（及格/优秀比例）、ScoreChangeServiceImpl（审批开关）、AuthService（维护模式）。
 *
 * @author School System
 */
public interface ScoreRuleService {

    /**
     * 及格比例：sys_config score.pass.line（百分数，如 60）/ 100，默认 0.60
     * <p>
     * 返回值用 compareTo 比较，不要用 equals（scale 可能不同）。
     */
    BigDecimal passRatio();

    /**
     * 优秀比例：sys_config score.excellent.line（百分数，如 85）/ 100，默认 0.85
     */
    BigDecimal excellentRatio();

    /**
     * 成绩修改是否需要审批：sys_config score.change.approval，默认 true
     */
    boolean changeApprovalRequired();

    /**
     * 系统是否处于维护模式：sys_config system.maintenance，默认 false
     */
    boolean maintenanceMode();

    /**
     * 清空本地缓存，配置被修改后由 ConfigController 调用，让新值立即生效而不用等 TTL
     */
    void evictCache();

}
