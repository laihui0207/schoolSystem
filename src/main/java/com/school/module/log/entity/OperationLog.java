package com.school.module.log.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 操作日志实体（sys_operation_log）
 * <p>
 * ponytail: 不继承 BaseEntity——表没有 updated_at / deleted 列，日志只追加不修改不软删；
 * created_at 也没有 @TableField(fill) 元数据，MetaObjectHandler 的 strictInsertFill 不会填它，
 * 由 OperationLogServiceImpl.record 在插入前手动赋值。
 *
 * @author School System
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_operation_log")
public class OperationLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 操作成功 */
    public static final int STATUS_SUCCESS = 1;
    /** 操作失败 */
    public static final int STATUS_FAIL = 0;

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 操作用户ID（sys_user.id），未登录请求（如登录失败）为 null
     */
    private Long userId;

    /**
     * 用户名；登录接口取请求参数里的 username
     */
    private String username;

    /**
     * 操作描述：@OperationLog 的 value，否则 Controller#method
     */
    private String operation;

    /**
     * HTTP 方法 + URI，如 "POST /api/v1/scores/review"
     */
    private String method;

    /**
     * 请求参数 JSON（敏感字段已打码，总长截 2000）
     */
    private String params;

    /**
     * 客户端 IP（X-Forwarded-For 优先）
     */
    private String ip;

    /**
     * 操作地点（暂未解析，预留）
     */
    private String location;

    /**
     * 状态：1-成功，0-失败
     */
    private Integer status;

    /**
     * 失败时的异常消息（截 500）
     */
    private String errorMsg;

    /**
     * 执行耗时（毫秒）
     */
    private Long executeTime;

    /**
     * 记录时间
     */
    private LocalDateTime createdAt;

}
