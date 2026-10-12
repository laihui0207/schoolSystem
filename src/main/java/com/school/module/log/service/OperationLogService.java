package com.school.module.log.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.module.log.dto.OperationLogQueryRequest;
import com.school.module.log.dto.OperationLogResponse;
import com.school.module.log.entity.OperationLog;

/**
 * 操作日志服务
 *
 * @author School System
 */
public interface OperationLogService {

    /**
     * 写入一条操作日志（切面调用；createdAt 为空时补当前时间）
     *
     * @param operationLog 日志实体
     */
    void record(OperationLog operationLog);

    /**
     * 分页查询操作日志
     * <p>
     * 超管不限校；校管理员只看本校用户产生的日志。
     *
     * @param request 筛选条件
     * @return 日志分页
     */
    Page<OperationLogResponse> listLogs(OperationLogQueryRequest request);

    /**
     * 查询单条日志详情（含 params / errorMsg 原文）
     *
     * @param id 日志ID
     * @return 日志
     */
    OperationLogResponse getLogById(Long id);

}
