package com.school.common.base;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;

/**
 * 分页查询基础类
 *
 * @author School System
 */
@Data
@SuperBuilder
@NoArgsConstructor
public class BasePageQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 页码
     */
    private Long pageNum = 1L;

    /**
     * 每页大小
     */
    private Long pageSize = 10L;

    /**
     * 排序字段
     */
    private String orderBy;

    /**
     * 是否升序(默认降序)
     */
    private Boolean isAsc = false;

}
