package com.school.module.log.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.module.log.dto.OperationLogQueryRequest;
import com.school.module.log.entity.OperationLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 操作日志 Mapper
 *
 * @author School System
 */
@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLog> {

    /**
     * 分页查询操作日志
     *
     * @param page     分页对象
     * @param request  筛选条件（用户名/操作/状态/时间范围）
     * @param schoolId 由 SecurityUtils.currentSchoolIdForQuery() 提供：超管为 null 不限校；
     *                 校管理员为本校 ID，通过 JOIN sys_user 过滤到本校用户产生的日志
     * @return 日志分页
     */
    Page<OperationLog> selectPageByQuery(@Param("page") Page<OperationLog> page,
                                         @Param("request") OperationLogQueryRequest request,
                                         @Param("schoolId") Long schoolId);

}
