package com.school.module.subject.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 更新科目请求DTO
 *
 * @author School System
 */
// ponytail: 只有 @Builder 时没有无参构造，@RequestBody 反序列化会在运行时炸
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubjectUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 科目名称
     */
    @Size(max = 50, message = "科目名称长度不能超过50个字符")
    private String subjectName;

    /**
     * 满分
     */
    @DecimalMin(value = "0.0", inclusive = false, message = "满分必须大于0")
    private BigDecimal fullScore;

    /**
     * 及格分
     */
    @DecimalMin(value = "0.0", inclusive = false, message = "及格分必须大于0")
    private BigDecimal passScore;

}

