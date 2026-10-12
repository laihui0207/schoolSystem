package com.school.module.student.roster;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 学生/教师档案批量导入结果
 * <p>
 * ponytail: 放在 student.roster 而非 common——两套导入（学生/教师）共用一份响应结构，
 * 但 common 包不属于本次改动范围；teacher 模块只读依赖这里。
 * 校验不通过时 successCount 为 0、failures 逐行列出原因，且不落任何数据。
 *
 * @author School System
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RosterImportResult {

    /**
     * 成功写入条数
     */
    private int successCount;

    /**
     * 校验失败条数
     */
    private int failCount;

    /**
     * 逐行失败明细
     */
    @Builder.Default
    private List<Failure> failures = new ArrayList<>();

    /**
     * 本次新开通的登录账号
     */
    @Builder.Default
    private List<CreatedAccount> createdAccounts = new ArrayList<>();

    /**
     * 非阻断提示（如同名班级/科目取了最新的一个）
     */
    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    /**
     * 单行失败明细
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Failure {
        /**
         * Excel 行号（表头为第 1 行，数据从第 2 行起）
         */
        private Integer row;

        /**
         * 学号（学生导入时填）
         */
        private String studentNo;

        /**
         * 工号（教师导入时填）
         */
        private String teacherNo;

        /**
         * 失败原因
         */
        private String reason;
    }

    /**
     * 新开通账号
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreatedAccount {
        private Integer row;
        private String username;
        private String realName;
        /**
         * 初始密码（统一为 123456，前端据此提示用户首次登录后修改）
         */
        private String initialPassword;
    }

}
