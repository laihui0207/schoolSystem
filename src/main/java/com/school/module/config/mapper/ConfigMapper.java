package com.school.module.config.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.school.module.config.entity.SysConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统配置Mapper
 *
 * @author School System
 */
@Mapper
public interface ConfigMapper extends BaseMapper<SysConfig> {
}
