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
     * <p>
     * PUT 以请求值为准：null（或不传）表示取消班主任。前端编辑弹窗总是携带该字段。
     */
    // ponytail: G-9 — Jackson 无法区分「未传」与「传 null」，约定 PUT 总是以请求值为准
    private Long headTeacherId;

}
