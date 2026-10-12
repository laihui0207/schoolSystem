package com.school.module.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 匿名可读的学校下拉项（注册页用）
 * <p>
 * ponytail: 只暴露 id 与名称，不复用 SchoolResponse——联系人电话、地址等不该匿名可见
 *
 * @author School System
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicSchoolResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String schoolName;

}
