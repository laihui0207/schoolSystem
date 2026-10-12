package com.school.module.export.service;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.school.common.exception.BusinessException;
import com.school.common.result.ResultCode;
import com.school.module.class_module.entity.SchoolClass;
import com.school.module.class_module.mapper.ClassMapper;
import com.school.module.exam.entity.Exam;
import com.school.module.exam.entity.ExamSubject;
import com.school.module.exam.mapper.ExamMapper;
import com.school.module.exam.mapper.ExamSubjectMapper;
import com.school.module.export.dto.ScoreExportExcel;
import com.school.module.export.dto.ScoreImportExcel;
import com.school.module.score.dto.BatchInputResult;
import com.school.module.score.dto.ScoreBatchInputRequest;
import com.school.module.score.entity.Score;
import com.school.module.score.mapper.ScoreMapper;
import com.school.module.score.service.ScoreService;
import com.school.module.student.entity.Student;
import com.school.module.student.mapper.ClassStudentMapper;
import com.school.module.student.mapper.StudentMapper;
import com.school.module.subject.entity.Subject;
import com.school.module.subject.mapper.SubjectMapper;
import com.school.security.service.TeacherScopeService;
import com.school.security.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 导入导出服务实现类
 *
 * @author School System
 */
@Service
@RequiredArgsConstructor
public class ExportServiceImpl implements ExportService {

    private final ScoreMapper scoreMapper;
    private final StudentMapper studentMapper;
    private final SubjectMapper subjectMapper;
    private final ScoreService scoreService;
    // ponytail: 以下四个只读注入——导出要填真实考试/班级名；导入要按 exam.school_id 定位学生、取 exam_subject 满分、校验班级归属
    private final ExamMapper examMapper;
    private final ClassMapper classMapper;
    private final ExamSubjectMapper examSubjectMapper;
    private final ClassStudentMapper classStudentMapper;
    // ponytail: B-4——导出要做教师班级范围校验
    private final TeacherScopeService teacherScopeService;

    private static final int EXAM_STATUS_PUBLISHED = 3;
    private static final int REMARK_MAX_LENGTH = 500;

    @Override
    public void exportScores(Long examId, Long classId, HttpServletResponse response) {
        // ponytail: B-4/契约 X——学生禁止导出；教师必须指定任教/班主任范围内的班级；非管理员只能导出已发布考试。
        // 此前该路径无任何身份判断，listScores 上加的 publishedOnly/studentId 限制在这里被整体绕开
        Exam exam = examMapper.selectById(examId);
        if (exam == null) {
            throw new BusinessException(ResultCode.EXAM_NOT_FOUND, "考试不存在");
        }
        checkExportScope(exam, classId);

        // 查询成绩数据
        List<Score> scores = scoreMapper.selectByExamId(SecurityUtils.currentSchoolIdForQuery(), examId, classId);

        if (scores.isEmpty()) {
            throw new BusinessException("暂无成绩数据");
        }

        // ponytail: 之前前 5 列写死占位字面量；这里各批量查一次建 Map，再逐行填真实值
        String examName = exam.getExamName() != null ? exam.getExamName() : "";

        Set<Long> studentIds = scores.stream().map(Score::getStudentId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> subjectIds = scores.stream().map(Score::getSubjectId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> classIds = scores.stream().map(Score::getClassId).filter(Objects::nonNull).collect(Collectors.toSet());

        Map<Long, Student> studentMap = studentIds.isEmpty() ? Map.of()
                : studentMapper.selectBatchIds(studentIds).stream()
                        .collect(Collectors.toMap(Student::getId, st -> st, (a, b) -> a));
        Map<Long, Subject> subjectMap = subjectIds.isEmpty() ? Map.of()
                : subjectMapper.selectBatchIds(subjectIds).stream()
                        .collect(Collectors.toMap(Subject::getId, sub -> sub, (a, b) -> a));
        Map<Long, SchoolClass> classMap = classIds.isEmpty() ? Map.of()
                : classMapper.selectBatchIds(classIds).stream()
                        .collect(Collectors.toMap(SchoolClass::getId, c -> c, (a, b) -> a));

        // 转换为导出格式
        List<ScoreExportExcel> exportData = scores.stream()
                .map(s -> convertToExportExcel(s, examName, studentMap, subjectMap, classMap))
                .collect(Collectors.toList());

        // 设置响应头
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            String fileName = URLEncoder.encode("成绩导出_" + System.currentTimeMillis(), StandardCharsets.UTF_8);
            response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");

            // 写入Excel
            EasyExcel.write(response.getOutputStream(), ScoreExportExcel.class)
                    .sheet("成绩单")
                    .doWrite(exportData);
        } catch (IOException e) {
            throw new BusinessException("导出失败: " + e.getMessage());
        }
    }

    @Override
    public void downloadScoreTemplate(HttpServletResponse response) {
        // 创建模板数据（示例）
        List<ScoreImportExcel> templateData = new ArrayList<>();
        ScoreImportExcel example = new ScoreImportExcel();
        example.setStudentNo("20240001");
        example.setStudentName("张三");
        example.setScore(BigDecimal.valueOf(95.5));
        example.setIsAbsent("否");
        example.setRemark("");
        templateData.add(example);

        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            String fileName = URLEncoder.encode("成绩导入模板", StandardCharsets.UTF_8);
            response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");

            EasyExcel.write(response.getOutputStream(), ScoreImportExcel.class)
                    .sheet("成绩模板")
                    .doWrite(templateData);
        } catch (IOException e) {
            throw new BusinessException("下载模板失败: " + e.getMessage());
        }
    }

    @Override
    public BatchInputResult importScores(Long examId, Long classId, Long subjectId, MultipartFile file) {
        List<ScoreImportExcel> dataList;
        try {
            dataList = EasyExcel.read(file.getInputStream())
                    .head(ScoreImportExcel.class)
                    .sheet()
                    .doReadSync();
        } catch (IOException e) {
            throw new BusinessException("读取Excel失败: " + e.getMessage());
        }

        if (dataList == null || dataList.isEmpty()) {
            throw new BusinessException("Excel文件为空");
        }

        // ponytail: 学号在 exam.school_id 范围内查找，不再用全局 selectByStudentNo——跨校同学号会串到别校学生
        Exam exam = examMapper.selectById(examId);
        if (exam == null) {
            throw new BusinessException(ResultCode.EXAM_NOT_FOUND, "考试不存在");
        }
        if (exam.getStatus() == null || exam.getStatus() != 1) {
            throw new BusinessException("考试不在录入中状态，无法导入成绩");
        }
        Long schoolId = exam.getSchoolId();

        // ponytail: 满分取 exam_subject 而非 subject，分数范围在服务端校验，不依赖前端
        ExamSubject examSubject = examSubjectMapper.selectByExamId(examId).stream()
                .filter(es -> subjectId.equals(es.getSubjectId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("该科目不在本次考试范围内"));
        BigDecimal fullScore = examSubject.getFullScore();

        Set<Long> classStudentIds = new HashSet<>(classStudentMapper.selectStudentIdsByClassId(classId));

        List<String> studentNos = dataList.stream()
                .map(ScoreImportExcel::getStudentNo)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(no -> !no.isEmpty())
                .distinct()
                .collect(Collectors.toList());
        Map<String, Student> studentByNo = studentNos.isEmpty() ? Map.of()
                : studentMapper.selectList(new LambdaQueryWrapper<Student>()
                        .eq(schoolId != null, Student::getSchoolId, schoolId)
                        .in(Student::getStudentNo, studentNos)).stream()
                        .collect(Collectors.toMap(Student::getStudentNo, st -> st, (a, b) -> a));

        // 先整体校验再落库：任一行无效就拒绝整批，避免导入一半留下脏数据
        List<ScoreBatchInputRequest.ScoreItem> items = new ArrayList<>();
        Set<String> seenNos = new HashSet<>();
        for (int i = 0; i < dataList.size(); i++) {
            ScoreImportExcel data = dataList.get(i);
            // 表头占第1行，数据行号从2开始
            int rowNum = i + 2;
            String studentNo = data.getStudentNo() != null ? data.getStudentNo().trim() : "";

            if (studentNo.isEmpty()) {
                throw new BusinessException("第" + rowNum + "行: 学号不能为空");
            }
            // ponytail: 文件内同一学号出现两次，后一行会静默覆盖前一行的成绩，必须报错
            if (!seenNos.add(studentNo)) {
                throw new BusinessException("第" + rowNum + "行: 学号 " + studentNo + " 重复");
            }
            Student student = studentByNo.get(studentNo);
            if (student == null) {
                throw new BusinessException("第" + rowNum + "行: 学号 " + studentNo + " 不存在");
            }
            if (!classStudentIds.contains(student.getId())) {
                throw new BusinessException("第" + rowNum + "行: 学生 " + studentNo + " 不在该班级");
            }

            boolean absent = data.absent();
            if (!absent) {
                BigDecimal score = data.getScore();
                if (score == null) {
                    throw new BusinessException("第" + rowNum + "行: 未缺考时必须填写分数");
                }
                boolean belowZero = score.compareTo(BigDecimal.ZERO) < 0;
                boolean aboveFull = fullScore != null && score.compareTo(fullScore) > 0;
                if (belowZero || aboveFull) {
                    String upper = fullScore != null ? fullScore.stripTrailingZeros().toPlainString() : "满分";
                    throw new BusinessException(ResultCode.SCORE_OUT_OF_RANGE,
                            "第" + rowNum + "行: 分数超出范围（0~" + upper + "）");
                }
            }

            // ponytail: B-7——score.remark 是 VARCHAR(500)，超长会让整批事务进入 aborted 状态，这里按行号前置拦截
            if (data.getRemark() != null && data.getRemark().length() > REMARK_MAX_LENGTH) {
                throw new BusinessException("第" + rowNum + "行: 备注不能超过" + REMARK_MAX_LENGTH + "字");
            }

            items.add(ScoreBatchInputRequest.ScoreItem.builder()
                    .studentId(student.getId())
                    .classId(classId)
                    .subjectId(subjectId)
                    .score(absent ? null : data.getScore())
                    .isAbsent(absent ? 1 : 0)
                    .remark(data.getRemark())
                    .build());
        }

        // schoolId 不再逐条传递：batchInputScores 按学生档案解析（契约 1）
        ScoreBatchInputRequest request = ScoreBatchInputRequest.builder()
                .examId(examId)
                .classId(classId)
                .subjectId(subjectId)
                .scores(items)
                .build();
        return scoreService.batchInputScores(request);
    }

    /**
     * 导出权限（契约 X）：学生 FORBIDDEN；管理员不限；教师必须指定任教/班主任范围内的班级；非管理员只能导出已发布考试
     */
    private void checkExportScope(Exam exam, Long classId) {
        if (SecurityUtils.isStudent()) {
            throw new BusinessException(ResultCode.FORBIDDEN, "学生账号无权导出成绩");
        }
        if (SecurityUtils.isSuperAdmin() || SecurityUtils.isSchoolAdmin()) {
            return;
        }
        if (!SecurityUtils.isTeacher()) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅管理员和教师可导出成绩");
        }
        if (exam.getStatus() == null || exam.getStatus() != EXAM_STATUS_PUBLISHED) {
            throw new BusinessException(ResultCode.FORBIDDEN, "考试尚未发布，暂不能导出成绩");
        }
        if (classId == null) {
            throw new BusinessException(ResultCode.FORBIDDEN, "教师账号必须指定班级");
        }
        if (!teacherScopeService.currentAllowedClassIds().contains(classId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权导出该班级的成绩");
        }
    }

    /**
     * 转换为导出Excel格式
     */
    private ScoreExportExcel convertToExportExcel(Score score, String examName,
                                                  Map<Long, Student> studentMap,
                                                  Map<Long, Subject> subjectMap,
                                                  Map<Long, SchoolClass> classMap) {
        Student student = studentMap.get(score.getStudentId());
        Subject subject = subjectMap.get(score.getSubjectId());
        SchoolClass schoolClass = classMap.get(score.getClassId());
        return ScoreExportExcel.builder()
                .examName(examName)
                .studentNo(student != null && student.getStudentNo() != null ? student.getStudentNo() : "")
                .studentName(student != null && student.getName() != null ? student.getName() : "")
                .className(schoolClass != null && schoolClass.getClassName() != null ? schoolClass.getClassName() : "")
                .subjectName(subject != null && subject.getSubjectName() != null ? subject.getSubjectName() : "")
                .score(score.getScore())
                .isAbsentText(score.getIsAbsent() != null && score.getIsAbsent() == 1 ? "是" : "否")
                .rankInClass(score.getRankInClass())
                .rankInGrade(score.getRankInGrade())
                .build();
    }

}
