package com.school.module.job.dto;

import com.school.common.base.BasePageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * 定时任务分页查询
 * <p>
 * ponytail: 父类 BasePageQuery 是 @SuperBuilder，子类同为 @SuperBuilder；显式无参构造给 Spring 绑定 GET 参数用
 *
 * @author School System
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class JobQueryRequest extends BasePageQuery {

    /**
     * 任务名称（模糊）
     */
    private String jobName;

    /**
     * 任务编码（模糊）
     */
    private String jobCode;

    /**
     * 0-停用，1-启用
     */
    private Integer status;

    public JobQueryRequest() {
        super();
    }

}
