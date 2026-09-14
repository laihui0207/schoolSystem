package com.school.module.config.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.school.module.config.entity.SysConfig;

/**
 * 系统配置服务接口
 *
 * @author School System
 */
public interface ConfigService extends IService<SysConfig> {

    /**
     * 根据键名获取配置值
     *
     * @param configKey 配置键
     * @return 配置值
     */
    String getConfigByKey(String configKey);

    /**
     * 更新配置值
     *
     * @param configKey 配置键
     * @param configValue 配置值
     * @return 是否成功
     */
    boolean updateConfigByKey(String configKey, String configValue);

}
