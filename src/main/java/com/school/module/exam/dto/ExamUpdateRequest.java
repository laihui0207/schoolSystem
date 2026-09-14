package com.school.module.exam.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 考试更新请求
 *
 * @author School System
 */
@Data
public class ExamUpdateRequest {

    /**
     * 考试名称
     */
    private String examName;

    /**
     * 考试类型：1-月考，2-期中，3-期末，4-模拟，5-其他
     */
    private Integer examType;

    /**
     * 考试日期
     */
    private LocalDate examDate;

    /**
     * 参与年级ID列表
     */
    private List<Long> gradeIds;

    /**
     * 科目信息列表
     */
    private List<ExamSubjectInfo> subjects;

    /**
     * 考试科目信息
     */
    @Data
    public static class ExamSubjectInfo {
        private Long subjectId;
        private BigDecimal fullScore;
        private Integer sortOrder;
    }

}
