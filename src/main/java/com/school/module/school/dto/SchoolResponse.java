package com.school.module.school.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学校响应
 *
 * @author School System
 */
@Data
@Builder
public class SchoolResponse {

    private Long id;
    private String schoolName;
    private String schoolCode;
    private String address;
    private String phone;
    private Integer status;
    private String statusName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
