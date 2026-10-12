package com.school.module.log.dto;

import com.school.common.base.BasePageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 操作日志分页查询请求
 * <p>
 * ponytail: 父类 BasePageQuery 是 @SuperBuilder，子类必须同为 @SuperBuilder 才能链式带上 pageNum/pageSize；
 * 显式无参构造是给 Spring 绑定 GET 查询参数用的（与 UserQueryRequest 同一套路）。
 *
 * @author School System
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class OperationLogQueryRequest extends BasePageQuery {

    /**
     * 用户名（模糊）
     */
    private String username;

    /**
     * 操作描述（模糊）
     */
    private String operation;

    /**
     * 状态：1-成功，0-失败
     */
    private Integer status;

    /**
     * 起始时间（含）
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    /**
     * 截止时间（含）
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    public OperationLogQueryRequest() {
        super();
    }

}
