package com.school.module.student.service;

import com.school.module.student.dto.StudentQueryRequest;
import com.school.module.student.roster.RosterImportResult;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * 学生档案批量导入导出
 * <p>
 * ponytail: 独立于 StudentService——导入要额外依赖用户服务、学期、sys_user，
 * 塞进 StudentServiceImpl 会让它逼近 700 行且迫使 StudentServiceTest 补一堆无关 @Mock。
 *
 * @author School System
 */
public interface StudentRosterService {

    /**
     * 下载导入模板（含一行示例）
     */
    void downloadTemplate(HttpServletResponse response);

    /**
     * 批量导入学生：整表校验通过才落库，任一行失败则一条都不写
     *
     * @param file     xlsx 文件
     * @param schoolId 目标学校；超管必填，校管理员可空（取当前学校，传了别校会被拒绝）
     * @return 成功/失败条数、逐行失败原因、新开账号
     */
    RosterImportResult importStudents(MultipartFile file, Long schoolId);

    /**
     * 按当前列表筛选条件导出学生档案（含班级、账号用户名）
     */
    void exportStudents(StudentQueryRequest request, HttpServletResponse response);

}
