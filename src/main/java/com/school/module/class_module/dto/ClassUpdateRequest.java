package com.school.module.class_module.dto;

import lombok.Data;

/**
 * 班级更新请求
 *
 * @author School System
 */
@Data
public class ClassUpdateRequest {

    /**
     * 班级名称
     */
    private String className;

    /**
     * 班级编码
     */
    private Integer classCode;

    /**
     * 班主任ID
     */
    private Long headTeacherId;

}
