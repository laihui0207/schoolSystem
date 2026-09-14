package com.school.ai.tools;

import com.school.security.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * AI 报表导出工具
 * 返回可直接访问的下载 URL，复用现有 ExportController 端点
 */
@Component
@RequiredArgsConstructor
public class ExportTools {

    private final AiPermissionChecker permissionChecker;

    @Tool(description = """
            导出成绩 Excel 报表，返回下载链接。
            前端会自动显示为可点击的下载按钮。
            根据当前用户权限自动过滤数据范围。
            """)
    public String exportScoreExcel(
            @ToolParam(description = "考试ID") Long examId,
            @ToolParam(description = "班级ID，可选，不传则导出全部有权限的班级") Long classId) {
        if (classId != null) {
            permissionChecker.assertCanViewClass(classId);
        } else {
            // 无 classId 时只有管理员才能导出全部
            var user = SecurityUtils.currentUser();
            if (!user.isSuperAdmin() && user.getUserType() != 2) {
                return "教师角色导出时请指定班级ID，例如：导出3班的成绩，班级ID=xxx";
            }
        }

        String url = classId != null
                ? "/api/v1/export/scores?examId=" + examId + "&classId=" + classId
                : "/api/v1/export/scores?examId=" + examId;

        // ponytail: __download__ 标记让前端识别并渲染为下载按钮
        return "__download__:" + url;
    }

    @Tool(description = "获取成绩导入模板 Excel 的下载链接，用于批量录入成绩前下载标准格式。")
    public String getScoreImportTemplate() {
        return "__download__:/api/v1/export/score-template";
    }

}
