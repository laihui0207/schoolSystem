package com.school.module.job.dto;

import com.school.common.base.BasePageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * 任务执行日志分页查询（jobId 来自路径）
 *
 * @author School System
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class JobLogQueryRequest extends BasePageQuery {

    /**
     * 1-成功，0-失败，2-执行中
     */
    private Integer status;

    public JobLogQueryRequest() {
        super();
    }

}
