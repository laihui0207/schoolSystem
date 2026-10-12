package com.school.module.job.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.school.module.job.entity.SysJob;
import org.apache.ibatis.annotations.Mapper;

/**
 * 定时任务 Mapper
 * <p>
 * ponytail: 纯 BaseMapper、无 XML——列表/日志分页用 LambdaQueryWrapper + selectPage 即可，
 * 两张表都没有 school_id，不需要手写 SQL 传 schoolId。
 *
 * @author School System
 */
@Mapper
public interface SysJobMapper extends BaseMapper<SysJob> {

}
