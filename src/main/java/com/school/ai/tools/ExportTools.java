package com.school.ai.tools;

import com.school.security.model.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * AI 报表导出工具
 * 返回相对 API 根的下载路径（不含 /api/v1 前缀），复用现有 ExportController 端点；
 * 前端 ChatPanel 自行拼接 utils/request.ts 的 baseURL 并带 Authorization 以 blob 方式下载
 */
@Component
@RequiredArgsConstructor
public class ExportTools {

    private final AiPermissionChecker permissionChecker;

    // ponytail: 跨组契约 4——下载路径相对 API 根，不再硬编码 /api/v1；前缀由前端 axios 实例的 baseURL 统一决定，
    // 否则后端 context-path 一变这里就 404，而且 a.href 直开新窗口不带 Authorization 必 401
    private static final String EXPORT_SCORES_PATH = "/export/scores";
    private static final String EXPORT_TEMPLATE_PATH = "/export/score-template";

    @Tool(description = """
            导出成绩 Excel 报表，返回下载链接。
            前端会自动显示为可点击的下载按钮。
            根据当前用户权限自动过滤数据范围。
            """)
    public String exportScoreExcel(
            @ToolParam(description = "考试ID") Long examId,
            @ToolParam(description = "班级ID，可选，不传则导出全部有权限的班级") Long classId,
            ToolContext toolContext) {
        if (classId != null) {
            permissionChecker.assertCanViewClass(toolContext, classId);
        } else {
            LoginUser user = permissionChecker.currentUser(toolContext);
            if (!user.isSuperAdmin() && user.getUserType() != 2) {
                return "教师角色导出时请指定班级ID，例如：导出3班的成绩，班级ID=xxx";
            }
        }
        String url = classId != null
                ? EXPORT_SCORES_PATH + "?examId=" + examId + "&classId=" + classId
                : EXPORT_SCORES_PATH + "?examId=" + examId;
        // ponytail: __download__ 标记让前端识别并渲染为下载按钮
        return "__download__:" + url;
    }

    @Tool(description = "获取成绩导入模板 Excel 的下载链接，用于批量录入成绩前下载标准格式。")
    public String getScoreImportTemplate(ToolContext toolContext) {
        return "__download__:" + EXPORT_TEMPLATE_PATH;
    }

}
