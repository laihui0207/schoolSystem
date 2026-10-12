package com.school.module.teacher.service;

import com.school.module.student.roster.RosterImportResult;
import com.school.module.teacher.dto.TeacherQueryRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * 教师档案批量导入导出
 * <p>
 * ponytail: 与 StudentRosterService 对称，独立于 TeacherService 以免其实现类和单测被导入依赖撑大。
 *
 * @author School System
 */
public interface TeacherRosterService {

    /**
     * 下载导入模板（含一行示例）
     */
    void downloadTemplate(HttpServletResponse response);

    /**
     * 批量导入教师：整表校验通过才落库，任一行失败则一条都不写
     *
     * @param file     xlsx 文件
     * @param schoolId 目标学校；超管必填，校管理员可空
     * @return 成功/失败条数、逐行失败原因、新开账号
     */
    RosterImportResult importTeachers(MultipartFile file, Long schoolId);

    /**
     * 按当前列表筛选条件导出教师档案（含主教科目、账号用户名）
     */
    void exportTeachers(TeacherQueryRequest request, HttpServletResponse response);

}
