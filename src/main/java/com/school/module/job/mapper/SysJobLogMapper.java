package com.school.module.job.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.school.module.job.entity.SysJobLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 定时任务执行日志 Mapper
 *
 * @author School System
 */
@Mapper
public interface SysJobLogMapper extends BaseMapper<SysJobLog> {

}
