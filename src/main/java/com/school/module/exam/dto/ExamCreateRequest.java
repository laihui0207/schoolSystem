package com.school.module.exam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 考试创建请求
 *
 * @author School System
 */
@Data
public class ExamCreateRequest {

    /**
     * 学校ID
     */
    @NotNull(message = "学校ID不能为空")
    private Long schoolId;

    /**
     * 学期ID
     */
    @NotNull(message = "学期ID不能为空")
    private Long termId;

    /**
     * 考试名称
     */
    @NotBlank(message = "考试名称不能为空")
    private String examName;

    /**
     * 考试类型：1-月考，2-期中，3-期末，4-模拟，5-其他
     */
    @NotNull(message = "考试类型不能为空")
    private Integer examType;

    /**
     * 考试日期
     */
    private LocalDate examDate;

    /**
     * 参与年级ID列表
     */
    @NotEmpty(message = "参与年级不能为空")
    private List<Long> gradeIds;

    /**
     * 科目信息列表
     */
    @NotEmpty(message = "考试科目不能为空")
    private List<ExamSubjectInfo> subjects;

    /**
     * 考试科目信息
     */
    @Data
    public static class ExamSubjectInfo {
        /**
         * 科目ID
         */
        @NotNull(message = "科目ID不能为空")
        private Long subjectId;

        /**
         * 满分
         */
        private BigDecimal fullScore;

        /**
         * 排序顺序
         */
        private Integer sortOrder;
    }

}
