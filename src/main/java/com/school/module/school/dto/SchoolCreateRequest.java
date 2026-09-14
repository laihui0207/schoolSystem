package com.school.module.school.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 学校创建/更新请求
 *
 * @author School System
 */
@Data
public class SchoolCreateRequest {

    @NotBlank(message = "学校名称不能为空")
    private String schoolName;

    @NotBlank(message = "学校编码不能为空")
    private String schoolCode;

    private String address;

    private String phone;

    /** 状态：0-禁用，1-启用 */
    private Integer status;

}
