package com.school.module.config.controller;

import com.school.common.result.Result;
import com.school.module.config.entity.SysConfig;
import com.school.module.config.service.ConfigService;
import com.school.module.config.service.ScoreRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 系统配置控制器
 *
 * @author School System
 */
@RestController
@RequestMapping("/configs")
@RequiredArgsConstructor
public class ConfigController {

    private final ConfigService configService;
    private final ScoreRuleService scoreRuleService;

    /**
     * 根据key获取配置
     */
    @GetMapping("/key/{configKey}")
    public Result<String> getConfigByKey(@PathVariable String configKey) {
        String value = configService.getConfigByKey(configKey);
        return Result.success(value);
    }

    /**
     * 查询所有配置
     */
    @GetMapping
    public Result<List<SysConfig>> listConfigs() {
        List<SysConfig> list = configService.list();
        return Result.success(list);
    }

    // ponytail: sys_config 没有 school_id，是全系统共享的；system.maintenance / score.change.approval 接入业务后，
    // 校管理员一旦能改就等于能锁住别校所有用户的登录，所以写操作只放给超管。
    // 每次写完清掉 ScoreRuleService 的 60 秒缓存，让开关立即生效。

    /**
     * 创建配置
     */
    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Result<Void> createConfig(@RequestBody SysConfig config) {
        configService.save(config);
        scoreRuleService.evictCache();
        return Result.success();
    }

    /**
     * 更新配置
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Result<Void> updateConfig(@PathVariable Long id, @RequestBody SysConfig config) {
        config.setId(id);
        configService.updateById(config);
        scoreRuleService.evictCache();
        return Result.success();
    }

    /**
     * 删除配置
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Result<Void> deleteConfig(@PathVariable Long id) {
        configService.removeById(id);
        scoreRuleService.evictCache();
        return Result.success();
    }

}

