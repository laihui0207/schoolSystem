package com.school.module.config.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.school.module.config.entity.SysConfig;
import com.school.module.config.mapper.ConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 系统配置服务实现类
 *
 * @author School System
 */
@Service
@RequiredArgsConstructor
public class ConfigServiceImpl extends ServiceImpl<ConfigMapper, SysConfig> implements ConfigService {

    @Override
    public String getConfigByKey(String configKey) {
        LambdaQueryWrapper<SysConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysConfig::getConfigKey, configKey);
        SysConfig config = this.getOne(wrapper);
        return config != null ? config.getConfigValue() : null;
    }

    @Override
    public boolean updateConfigByKey(String configKey, String configValue) {
        LambdaQueryWrapper<SysConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysConfig::getConfigKey, configKey);
        SysConfig config = this.getOne(wrapper);
        if (config != null) {
            config.setConfigValue(configValue);
            return this.updateById(config);
        }
        return false;
    }

}
