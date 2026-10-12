package com.school.module.school.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 更新学校请求DTO
 *
 * @author School System
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SchoolUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 学校名称
     */
    @Size(max = 100, message = "学校名称长度不能超过100个字符")
    private String schoolName;

    /**
     * 区域编码
     */
    @Size(max = 20, message = "区域编码长度不能超过20个字符")
    private String regionCode;

    /**
     * 学校类型
     */
    @Size(max = 20, message = "学校类型长度不能超过20个字符")
    private String schoolType;

    /**
     * 省份
     */
    @Size(max = 50, message = "省份长度不能超过50个字符")
    private String province;

    /**
     * 城市
     */
    @Size(max = 50, message = "城市长度不能超过50个字符")
    private String city;

    /**
     * 区县
     */
    @Size(max = 50, message = "区县长度不能超过50个字符")
    private String district;

    /**
     * 详细地址
     */
    @Size(max = 255, message = "详细地址长度不能超过255个字符")
    private String address;

    /**
     * 联系人
     */
    @Size(max = 50, message = "联系人长度不能超过50个字符")
    private String contactPerson;

    /**
     * 联系电话
     */
    // ponytail: G-6 — contact_phone 为 NULL 的学校编辑时前端回填 ''，且座机号必须能通过
    @Pattern(regexp = "^$|^(1[3-9]\\d{9}|0\\d{2,3}-?\\d{7,8})$", message = "联系电话格式不正确")
    private String contactPhone;

}
