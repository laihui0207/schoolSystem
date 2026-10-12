package com.school.common.base;

import com.school.common.exception.BusinessException;
import com.school.common.result.ResultCode;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;

/**
 * 分页查询基础类
 *
 * @author School System
 */
@Data
@SuperBuilder
public class BasePageQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    @Builder.Default
    private Long pageNum = 1L;

    @Builder.Default
    private Long pageSize = 10L;

    /**
     * 排序字段
     * <p>
     * 所有子类的 mapper 均用 {@code ORDER BY ${request.orderBy}} 直接拼接，
     * 客户端可控的字符串在此校验：只允许列名合法字符（字母、数字、下划线），
     * 拒绝空格、引号、括号等 SQL 注入载体。
     */
    private String orderBy;

    @Builder.Default
    private Boolean isAsc = false;

    // ponytail: @Builder.Default 会把字段初始值搬进 builder，手写的无参构造里字段仍是 null。
    // Spring 用 @ModelAttribute 绑定 GET 查询参数时正是走这个构造，客户端不传 pageNum/pageSize
    // 就会在 new Page<>(null, null) 处 NPE 变成 500。这里显式补默认值，与 builder 保持一致。
    public BasePageQuery() {
        this.pageNum = 1L;
        this.pageSize = 10L;
        this.isAsc = false;
    }

    // ponytail: ORDER BY ${orderBy} — 正则拦截注入字符；仅当字段需要复杂表达式时换白名单
    public void setOrderBy(String orderBy) {
        if (orderBy != null && !orderBy.matches("[a-zA-Z0-9_]+")) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "排序字段含非法字符");
        }
        this.orderBy = orderBy;
    }

}
