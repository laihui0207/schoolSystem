package com.school.module.school.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.school.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 学校实体
 *
 * @author School System
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("school")
public class School extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 学校名称 */
    private String schoolName;

    /** 学校编码 */
    private String schoolCode;

    /** 地址 */
    private String address;

    /** 联系电话 */
    @TableField("contact_phone")
    private String phone;

    /** 状态：0-禁用，1-启用 */
    private Integer status;

}
