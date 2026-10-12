package com.school.module.student.roster;

import com.alibaba.excel.EasyExcel;
import com.school.common.exception.BusinessException;
import com.school.common.result.ResultCode;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * 名册 Excel 读写
 * <p>
 * 与 ExportServiceImpl 相同的响应头约定（attachment + RFC 5987 文件名），
 * 这里抽出来让学生/教师两套模板、导出共用。
 *
 * @author School System
 */
public final class RosterExcel {

    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    // ponytail: 单次导入上限——每行开户要走用户名唯一校验 + BCrypt + 角色绑定，两千行以内一次事务可接受
    public static final int MAX_IMPORT_ROWS = 2000;

    private RosterExcel() {
    }

    /**
     * 读取上传文件的第一个 sheet
     *
     * @throws BusinessException 文件为空、非 xlsx/xls、行数超限或无法解析
     */
    public static <T> List<T> read(MultipartFile file, Class<T> head) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.FILE_UPLOAD_ERROR, "请选择要导入的 Excel 文件");
        }
        String name = file.getOriginalFilename() == null
                ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".xlsx") && !name.endsWith(".xls")) {
            throw new BusinessException(ResultCode.FILE_TYPE_ERROR, "只支持 .xlsx 或 .xls 文件");
        }
        List<T> rows;
        try {
            rows = EasyExcel.read(file.getInputStream()).head(head).sheet().doReadSync();
        } catch (IOException e) {
            throw new BusinessException(ResultCode.FILE_UPLOAD_ERROR, "读取 Excel 失败: " + e.getMessage());
        } catch (RuntimeException e) {
            throw new BusinessException(ResultCode.FILE_TYPE_ERROR, "Excel 解析失败，请使用下载的模板填写");
        }
        if (rows == null || rows.isEmpty()) {
            throw new BusinessException("Excel 文件没有数据行");
        }
        if (rows.size() > MAX_IMPORT_ROWS) {
            throw new BusinessException("单次最多导入 " + MAX_IMPORT_ROWS + " 行，请拆分文件");
        }
        return rows;
    }

    /**
     * 以附件形式写出 xlsx
     */
    public static <T> void write(HttpServletResponse response, String fileName, String sheetName,
                                 Class<T> head, List<T> rows) {
        try {
            response.setContentType(XLSX_CONTENT_TYPE);
            response.setCharacterEncoding("utf-8");
            String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8);
            response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + encoded + ".xlsx");
            EasyExcel.write(response.getOutputStream(), head).sheet(sheetName).doWrite(rows);
        } catch (IOException e) {
            throw new BusinessException("导出失败: " + e.getMessage());
        }
    }

}
