package com.school.module.config.controller;

import com.school.common.result.Result;
import com.school.module.config.entity.SysConfig;
import com.school.module.config.service.ConfigService;
import lombok.RequiredArgsConstructor;
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

    /**
     * 创建配置
     */
    @PostMapping
    public Result<Void> createConfig(@RequestBody SysConfig config) {
        configService.save(config);
        return Result.success();
    }

    /**
     * 更新配置
     */
    @PutMapping("/{id}")
    public Result<Void> updateConfig(@PathVariable Long id, @RequestBody SysConfig config) {
        config.setId(id);
        configService.updateById(config);
        return Result.success();
    }

    /**
     * 删除配置
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteConfig(@PathVariable Long id) {
        configService.removeById(id);
        return Result.success();
    }

}

