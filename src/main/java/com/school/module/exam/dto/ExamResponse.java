package com.school.module.exam.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 考试响应
 *
 * @author School System
 */
@Data
@Builder
public class ExamResponse {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 学校ID
     */
    private Long schoolId;

    /**
     * 学校名称
     */
    private String schoolName;

    /**
     * 学期ID
     */
    private Long termId;

    /**
     * 学期名称
     */
    private String termName;

    /**
     * 考试名称
     */
    private String examName;

    /**
     * 考试类型：1-月考，2-期中，3-期末，4-模拟，5-其他
     */
    private Integer examType;

    /**
     * 考试类型名称
     */
    private String examTypeName;

    /**
     * 考试日期
     */
    private LocalDate examDate;

    /**
     * 参与年级ID列表
     */
    private List<Long> gradeIds;

    /**
     * 参与年级名称列表
     */
    private List<String> gradeNames;

    /**
     * 状态：0-未开始，1-录入中，2-审核中，3-已发布
     */
    private Integer status;

    /**
     * 状态名称
     */
    private String statusName;

    /**
     * 科目信息列表
     */
    private List<SubjectInfo> subjects;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 科目信息
     */
    @Data
    @Builder
    public static class SubjectInfo {
        private Long subjectId;
        private String subjectName;
        private BigDecimal fullScore;
        private Integer sortOrder;
    }

}
