package com.school.module.score.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 批量成绩录入结果（契约 1 响应 data）
 * <p>
 * ponytail: 单条失败不再只写日志，逐条记入 failures 让前端能定位到具体学生/科目
 *
 * @author School System
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchInputResult {

    /**
     * 成功条数
     */
    private int successCount;

    /**
     * 失败条数
     */
    private int failCount;

    /**
     * 失败明细
     */
    private List<Failure> failures;

    /**
     * 单条失败明细
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Failure {
        /**
         * 学生ID
         */
        private Long studentId;

        /**
         * 科目ID
         */
        private Long subjectId;

        /**
         * 失败原因
         */
        private String reason;
    }

}
