package com.school.module.log.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.common.result.Result;
import com.school.module.log.dto.OperationLogQueryRequest;
import com.school.module.log.dto.OperationLogResponse;
import com.school.module.log.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 操作日志控制器
 * <p>
 * 只读接口：日志由 OperationLogAspect 自动写入，没有手工新增/删除入口。
 *
 * @author School System
 */
@RestController
@RequestMapping("/operation-logs")
@RequiredArgsConstructor
public class OperationLogController {

    private final OperationLogService operationLogService;

    /**
     * 分页查询操作日志（超管全量；校管理员仅本校用户）
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")
    public Result<Page<OperationLogResponse>> listLogs(OperationLogQueryRequest request) {
        return Result.success(operationLogService.listLogs(request));
    }

    /**
     * 查询单条日志详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")
    public Result<OperationLogResponse> getLogById(@PathVariable Long id) {
        return Result.success(operationLogService.getLogById(id));
    }

}
