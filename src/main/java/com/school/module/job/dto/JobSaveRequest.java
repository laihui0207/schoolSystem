package com.school.module.job.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 新建 / 编辑定时任务请求
 * <p>
 * cron 合法性与 handlerBean 是否存在不能用注解表达，在 JobServiceImpl 里校验。
 *
 * @author School System
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobSaveRequest {

    @NotBlank(message = "任务名称不能为空")
    @Size(max = 100, message = "任务名称长度不能超过100个字符")
    private String jobName;

    /**
     * 任务编码：调度器 Map 的 key；字母开头，字母数字下划线中划线
     */
    @NotBlank(message = "任务编码不能为空")
    @Size(max = 50, message = "任务编码长度不能超过50个字符")
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_\\-]*$", message = "任务编码须以字母开头，只能包含字母、数字、下划线、中划线")
    private String jobCode;

    /**
     * 执行器 Bean 名，取自 GET /jobs/handlers
     */
    @NotBlank(message = "任务处理器不能为空")
    @Size(max = 100, message = "任务处理器名称长度不能超过100个字符")
    private String handlerBean;

    /**
     * Spring 6 位 cron（秒 分 时 日 月 周）
     */
    @NotBlank(message = "cron 表达式不能为空")
    @Size(max = 50, message = "cron 表达式长度不能超过50个字符")
    private String cron;

    /**
     * JSON 对象字符串，可空
     */
    @Size(max = 2000, message = "任务参数长度不能超过2000个字符")
    private String params;

    /**
     * 0-停用，1-启用；不传默认停用
     */
    @Min(value = 0, message = "状态只能是 0 或 1")
    @Max(value = 1, message = "状态只能是 0 或 1")
    private Integer status;

    @Size(max = 500, message = "备注长度不能超过500个字符")
    private String remark;

}
