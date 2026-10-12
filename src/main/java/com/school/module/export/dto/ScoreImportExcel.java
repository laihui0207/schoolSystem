package com.school.module.export.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 成绩导入Excel模板（同时用于下载模板与读取导入文件）
 * <p>
 * 列顺序/表头须与前端 ScoreImport.vue 的校验保持一致：学号 | 姓名 | 分数 | 缺考 | 备注。
 * 导入按 考试/班级/科目 三元组进行，科目由请求参数指定，模板中不含科目列。
 *
 * @author School System
 */
@Data
public class ScoreImportExcel {

    @ExcelProperty(value = "学号", index = 0)
    private String studentNo;

    @ExcelProperty(value = "姓名", index = 1)
    private String studentName;

    @ExcelProperty(value = "分数", index = 2)
    private BigDecimal score;

    /**
     * 缺考：是/否（兼容 1/0）
     */
    @ExcelProperty(value = "缺考", index = 3)
    private String isAbsent;

    /**
     * 备注：随成绩一起写入 score.remark
     * <p>
     * ponytail: 此前注释说"导入时忽略"，现在 importScores 会把它透传到 ScoreItem.remark
     */
    @ExcelProperty(value = "备注", index = 4)
    private String remark;

    /**
     * 是否缺考
     */
    public boolean absent() {
        if (isAbsent == null) {
            return false;
        }
        String v = isAbsent.trim();
        // ponytail: 模板取值只有 是/否 或 1/0；"1.0" 是 Excel 数字单元格被读成字符串的形态
        return "是".equals(v) || "1".equals(v) || "1.0".equals(v);
    }

}
