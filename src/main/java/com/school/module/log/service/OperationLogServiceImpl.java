package com.school.module.log.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.common.exception.BusinessException;
import com.school.common.result.ResultCode;
import com.school.module.log.dto.OperationLogQueryRequest;
import com.school.module.log.dto.OperationLogResponse;
import com.school.module.log.entity.OperationLog;
import com.school.module.log.mapper.OperationLogMapper;
import com.school.module.user.entity.SysUser;
import com.school.module.user.mapper.SysUserMapper;
import com.school.security.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 操作日志服务实现
 *
 * @author School System
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OperationLogServiceImpl implements OperationLogService {

    private final OperationLogMapper operationLogMapper;
    private final SysUserMapper sysUserMapper;

    @Override
    public void record(OperationLog operationLog) {
        if (operationLog.getCreatedAt() == null) {
            operationLog.setCreatedAt(LocalDateTime.now());
        }
        // ponytail: 不开事务、不走 @Async——切面在 Controller 外层执行，业务事务已提交/回滚，
        // 这里同步 insert 自动提交；日志写失败由切面 catch 后 warn，不反向影响业务结果
        operationLogMapper.insert(operationLog);
    }

    @Override
    public Page<OperationLogResponse> listLogs(OperationLogQueryRequest request) {
        // ponytail: 超管 null 不限校；校管理员本校 ID 走 XML 里的 JOIN sys_user 过滤。不能用请求参数替代
        Long schoolId = SecurityUtils.currentSchoolIdForQuery();
        Page<OperationLog> page = new Page<>(request.getPageNum(), request.getPageSize());
        Page<OperationLog> result = operationLogMapper.selectPageByQuery(page, request, schoolId);

        List<OperationLogResponse> records = result.getRecords().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());

        Page<OperationLogResponse> responsePage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        responsePage.setRecords(records);
        return responsePage;
    }

    @Override
    public OperationLogResponse getLogById(Long id) {
        OperationLog operationLog = operationLogMapper.selectById(id);
        if (operationLog == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "操作日志不存在");
        }
        assertVisible(operationLog);
        return convertToResponse(operationLog);
    }

    /**
     * 单条详情的租户校验：与列表 JOIN 口径一致，校管理员只能看本校用户的日志；
     * 匿名日志（user_id 为 null）只有超管可见。
     */
    private void assertVisible(OperationLog operationLog) {
        if (SecurityUtils.isSuperAdmin()) {
            return;
        }
        Long schoolId = SecurityUtils.currentSchoolId();
        if (operationLog.getUserId() == null) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权查看该操作日志");
        }
        SysUser user = sysUserMapper.selectById(operationLog.getUserId());
        if (user == null || !schoolId.equals(user.getSchoolId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权查看该操作日志");
        }
    }

    private OperationLogResponse convertToResponse(OperationLog operationLog) {
        return OperationLogResponse.builder()
                .id(operationLog.getId())
                .userId(operationLog.getUserId())
                .username(operationLog.getUsername())
                .operation(operationLog.getOperation())
                .method(operationLog.getMethod())
                .params(operationLog.getParams())
                .ip(operationLog.getIp())
                .location(operationLog.getLocation())
                .status(operationLog.getStatus())
                .statusName(statusName(operationLog.getStatus()))
                .errorMsg(operationLog.getErrorMsg())
                .executeTime(operationLog.getExecuteTime())
                .createdAt(operationLog.getCreatedAt())
                .build();
    }

    private static String statusName(Integer status) {
        if (status == null) {
            return null;
        }
        return status == OperationLog.STATUS_SUCCESS ? "成功" : "失败";
    }

}
