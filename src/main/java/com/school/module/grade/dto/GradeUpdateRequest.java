package com.school.module.grade.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 年级更新请求
 *
 * @author School System
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GradeUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 年级名称
     */
    @Size(max = 50, message = "年级名称长度不能超过50个字符")
    private String gradeName;

    /**
     * 年级编码
     */
    private Integer gradeCode;

}

