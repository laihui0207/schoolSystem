package com.school.module.job.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.school.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 定时任务定义（sys_job）
 *
 * @author School System
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_job")
public class SysJob extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 停用 */
    public static final int STATUS_DISABLED = 0;
    /** 启用 */
    public static final int STATUS_ENABLED = 1;

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 任务名称
     */
    private String jobName;

    /**
     * 任务编码：调度器 Map 的 key，未删除行内唯一
     */
    private String jobCode;

    /**
     * 执行器 Bean 名（实现 JobHandler）
     */
    private String handlerBean;

    /**
     * Spring 6 位 cron（秒 分 时 日 月 周）
     */
    private String cron;

    /**
     * 传给 handler 的 JSON 对象字符串
     */
    private String params;

    /**
     * 0-停用，1-启用
     */
    private Integer status;

    /**
     * 最近一次执行结束时间
     */
    private LocalDateTime lastRunAt;

    /**
     * 最近一次执行结果：1-成功，0-失败
     */
    private Integer lastRunStatus;

    /**
     * 下次触发时间（停用时为空）
     */
    private LocalDateTime nextRunAt;

    private String remark;

}
