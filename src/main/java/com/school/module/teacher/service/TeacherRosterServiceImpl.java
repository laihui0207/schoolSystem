package com.school.module.teacher.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.common.constant.CommonConstants;
import com.school.common.exception.BusinessException;
import com.school.module.school.entity.School;
import com.school.module.school.mapper.SchoolMapper;
import com.school.module.student.roster.RosterCells;
import com.school.module.student.roster.RosterExcel;
import com.school.module.student.roster.RosterImportResult;
import com.school.module.subject.entity.Subject;
import com.school.module.subject.mapper.SubjectMapper;
import com.school.module.teacher.dto.TeacherExportExcel;
import com.school.module.teacher.dto.TeacherImportExcel;
import com.school.module.teacher.dto.TeacherQueryRequest;
import com.school.module.teacher.entity.Teacher;
import com.school.module.teacher.mapper.TeacherMapper;
import com.school.module.user.dto.UserCreateRequest;
import com.school.module.user.dto.UserResponse;
import com.school.module.user.entity.SysUser;
import com.school.module.user.enums.UserRole;
import com.school.module.user.mapper.SysUserMapper;
import com.school.module.user.service.UserService;
import com.school.security.util.SecurityUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 教师档案批量导入导出实现
 *
 * @author School System
 */
@Service
@RequiredArgsConstructor
public class TeacherRosterServiceImpl implements TeacherRosterService {

    private final TeacherMapper teacherMapper;
    // ponytail: 以下均为只读注入——按科目名定位本校科目、回填学校/科目名、预检用户名占用；不往对方模块加方法
    private final SubjectMapper subjectMapper;
    private final SchoolMapper schoolMapper;
    private final SysUserMapper sysUserMapper;
    // ponytail: 开户复用 UserService.createUser，与学生导入同一条路径
    private final UserService userService;

    private static final String TEMPLATE_FILE = "教师导入模板";
    private static final String EXPORT_FILE_PREFIX = "教师档案导出_";
    private static final long EXPORT_MAX_ROWS = 50_000L;
    private static final int TEACHER_NO_MAX = 30;
    private static final int NAME_MAX = 50;

    /**
     * 校验通过、待落库的一行
     */
    private record PreparedTeacher(int rowNum, String teacherNo, String name, Integer gender,
                                   Subject subject, boolean openAccount) {
    }

    /**
     * 整表预加载的参照数据
     */
    private record ImportContext(Map<String, Teacher> existingByNo,
                                 Map<String, Subject> subjectByName,
                                 Set<String> takenUsernames,
                                 List<String> warnings) {
    }

    @Override
    public void downloadTemplate(HttpServletResponse response) {
        TeacherImportExcel example = new TeacherImportExcel();
        example.setTeacherNo("T20240001");
        example.setName("李老师");
        example.setGender("女");
        example.setSubjectName("数学");
        example.setOpenAccount("是");
        RosterExcel.write(response, TEMPLATE_FILE, "教师导入", TeacherImportExcel.class, List.of(example));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RosterImportResult importTeachers(MultipartFile file, Long requestedSchoolId) {
        Long schoolId = SecurityUtils.resolveSchoolId(requestedSchoolId);
        List<TeacherImportExcel> rows = RosterExcel.read(file, TeacherImportExcel.class);
        ImportContext ctx = loadContext(schoolId, rows);

        List<PreparedTeacher> prepared = new ArrayList<>();
        List<RosterImportResult.Failure> failures = new ArrayList<>();
        Set<String> seenNos = new HashSet<>();
        for (int i = 0; i < rows.size(); i++) {
            TeacherImportExcel row = rows.get(i);
            if (row.isBlankRow()) {
                continue;
            }
            int rowNum = i + 2;
            try {
                prepared.add(validateRow(rowNum, row, ctx, seenNos, schoolId));
            } catch (IllegalArgumentException e) {
                failures.add(RosterImportResult.Failure.builder()
                        .row(rowNum)
                        .teacherNo(RosterCells.normalizeCode(row.getTeacherNo()))
                        .reason(e.getMessage())
                        .build());
            }
        }

        if (prepared.isEmpty() && failures.isEmpty()) {
            throw new BusinessException("Excel 没有可导入的数据行");
        }
        if (!failures.isEmpty()) {
            // ponytail: 任一行无效就整批不落库，与学生导入保持同一语义
            return RosterImportResult.builder()
                    .successCount(0)
                    .failCount(failures.size())
                    .failures(failures)
                    .warnings(ctx.warnings())
                    .build();
        }

        List<RosterImportResult.CreatedAccount> accounts = new ArrayList<>();
        for (PreparedTeacher p : prepared) {
            persist(p, schoolId, accounts);
        }
        return RosterImportResult.builder()
                .successCount(prepared.size())
                .failCount(0)
                .createdAccounts(accounts)
                .warnings(ctx.warnings())
                .build();
    }

    @Override
    public void exportTeachers(TeacherQueryRequest request, HttpServletResponse response) {
        if (!SecurityUtils.isSuperAdmin()) {
            request.setSchoolId(SecurityUtils.currentSchoolId());
        }
        // ponytail: 复用列表页 SQL 保证「导出 = 当前筛选」；size=-1 + maxLimit 只加 LIMIT 上限、不做 COUNT
        Page<Teacher> page = new Page<>(1, -1, false);
        page.setMaxLimit(EXPORT_MAX_ROWS);
        List<Teacher> teachers = teacherMapper.selectPageByQuery(page, request).getRecords();
        if (teachers == null || teachers.isEmpty()) {
            throw new BusinessException("当前筛选条件下没有教师数据");
        }

        Map<Long, String> schoolNames = loadSchoolNames(teachers);
        Map<Long, String> subjectNames = loadSubjectNames(teachers);
        Map<Long, String> usernames = loadUsernames(teachers);

        List<TeacherExportExcel> data = teachers.stream()
                .map(t -> toExportRow(t, schoolNames, subjectNames, usernames))
                .collect(Collectors.toList());
        RosterExcel.write(response, EXPORT_FILE_PREFIX + System.currentTimeMillis(), "教师档案",
                TeacherExportExcel.class, data);
    }

    // ---------------------------------------------------------------- 导入：预加载

    private ImportContext loadContext(Long schoolId, List<TeacherImportExcel> rows) {
        List<String> nos = rows.stream()
                .map(r -> RosterCells.normalizeCode(r.getTeacherNo()))
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        // ponytail: teacher_no 在 DDL（V2）里是全局 UNIQUE，不按学校过滤；校管理员看不到的别校占用由 persist 的唯一索引兜底
        Map<String, Teacher> existingByNo = nos.isEmpty() ? Map.of()
                : teacherMapper.selectList(new LambdaQueryWrapper<Teacher>().in(Teacher::getTeacherNo, nos)).stream()
                        .collect(Collectors.toMap(Teacher::getTeacherNo, t -> t, (a, b) -> a));

        // ponytail: 不用 .select 投影，原因见 StudentRosterServiceImpl.loadContext
        Set<String> takenUsernames = nos.isEmpty() ? Set.of()
                : sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .in(SysUser::getUsername, nos)).stream()
                        .map(SysUser::getUsername)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

        Set<String> subjectNames = rows.stream()
                .map(r -> RosterCells.trimToNull(r.getSubjectName()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<String> warnings = new ArrayList<>();
        Map<String, Subject> subjectByName = resolveSubjects(schoolId, subjectNames, warnings);

        return new ImportContext(existingByNo, subjectByName, takenUsernames, warnings);
    }

    /**
     * 按科目名定位本校科目；同名多条时取 id 最大的并提示
     */
    private Map<String, Subject> resolveSubjects(Long schoolId, Set<String> names, List<String> warnings) {
        if (names.isEmpty()) {
            return Map.of();
        }
        List<Subject> subjects = subjectMapper.selectList(new LambdaQueryWrapper<Subject>()
                .eq(Subject::getSchoolId, schoolId)
                .in(Subject::getSubjectName, names));
        Map<String, List<Subject>> grouped = subjects.stream()
                .filter(s -> s.getSubjectName() != null)
                .collect(Collectors.groupingBy(Subject::getSubjectName));

        Map<String, Subject> result = new HashMap<>();
        grouped.forEach((name, list) -> {
            // ponytail: 科目没有学期维度，同名只能按 id 取最新建的一条，并在结果里提示管理员核对
            Subject chosen = list.stream()
                    .max(Comparator.comparingLong(s -> s.getId() == null ? Long.MIN_VALUE : s.getId()))
                    .orElse(list.get(0));
            if (list.size() > 1) {
                warnings.add("科目「" + name + "」有 " + list.size() + " 条同名记录，已选用最新的一条（ID " + chosen.getId() + "）");
            }
            result.put(name, chosen);
        });
        return result;
    }

    // ---------------------------------------------------------------- 导入：逐行校验

    private PreparedTeacher validateRow(int rowNum, TeacherImportExcel row, ImportContext ctx,
                                        Set<String> seenNos, Long schoolId) {
        String teacherNo = RosterCells.normalizeCode(row.getTeacherNo());
        if (teacherNo == null) {
            throw new IllegalArgumentException("工号不能为空");
        }
        if (teacherNo.length() > TEACHER_NO_MAX) {
            throw new IllegalArgumentException("工号不能超过 " + TEACHER_NO_MAX + " 个字符");
        }
        if (!seenNos.add(teacherNo)) {
            throw new IllegalArgumentException("工号在文件内重复");
        }
        Teacher existing = ctx.existingByNo().get(teacherNo);
        if (existing != null) {
            throw new IllegalArgumentException(schoolId.equals(existing.getSchoolId())
                    ? "工号已存在" : "工号已被其他学校使用（工号全局唯一）");
        }

        String name = RosterCells.trimToNull(row.getName());
        if (name == null) {
            throw new IllegalArgumentException("姓名不能为空");
        }
        if (name.length() > NAME_MAX) {
            throw new IllegalArgumentException("姓名不能超过 " + NAME_MAX + " 个字符");
        }

        Integer gender = RosterCells.parseGender(row.getGender());

        String subjectName = RosterCells.trimToNull(row.getSubjectName());
        Subject subject = null;
        if (subjectName != null) {
            subject = ctx.subjectByName().get(subjectName);
            if (subject == null) {
                throw new IllegalArgumentException("科目「" + subjectName + "」在本校不存在");
            }
        }

        boolean openAccount = RosterCells.parseYesNo(row.getOpenAccount(), "是否开通账号");
        if (openAccount && ctx.takenUsernames().contains(teacherNo)) {
            throw new IllegalArgumentException("用户名 " + teacherNo + " 已被占用，无法开通账号");
        }

        return new PreparedTeacher(rowNum, teacherNo, name, gender, subject, openAccount);
    }

    // ---------------------------------------------------------------- 导入：落库

    private void persist(PreparedTeacher p, Long schoolId, List<RosterImportResult.CreatedAccount> accounts) {
        Long userId = null;
        if (p.openAccount()) {
            userId = createAccount(p, schoolId);
            accounts.add(RosterImportResult.CreatedAccount.builder()
                    .row(p.rowNum())
                    .username(p.teacherNo())
                    .realName(p.name())
                    .initialPassword(RosterCells.DEFAULT_PASSWORD)
                    .build());
        }

        Teacher teacher = new Teacher();
        teacher.setTeacherNo(p.teacherNo());
        teacher.setSchoolId(schoolId);
        teacher.setName(p.name());
        teacher.setGender(p.gender());
        teacher.setSubjectId(p.subject() == null ? null : p.subject().getId());
        teacher.setUserId(userId);
        teacher.setStatus(1);
        try {
            teacherMapper.insert(teacher);
        } catch (DuplicateKeyException e) {
            // ponytail: 校管理员看不到别校的同工号行（租户过滤），只能靠唯一索引兜底；带行号抛出，事务整批回滚
            throw new BusinessException("第" + p.rowNum() + "行: 工号 " + p.teacherNo()
                    + " 已被占用（工号全局唯一），本次导入已全部回滚");
        }
    }

    private Long createAccount(PreparedTeacher p, Long schoolId) {
        UserCreateRequest request = new UserCreateRequest();
        request.setUsername(p.teacherNo());
        request.setPassword(RosterCells.DEFAULT_PASSWORD);
        request.setRealName(p.name());
        request.setRole(UserRole.TEACHER.getCode());
        request.setSchoolId(schoolId);
        request.setStatus(CommonConstants.STATUS_ENABLED);
        try {
            UserResponse created = userService.createUser(request);
            return created.getId();
        } catch (BusinessException e) {
            throw new BusinessException(e.getCode(), "第" + p.rowNum() + "行: 开通账号失败——" + e.getMessage());
        } catch (DuplicateKeyException e) {
            // ponytail: 预检只看 deleted=0 的用户，被逻辑删除的同名账号仍占着 username 唯一索引
            throw new BusinessException("第" + p.rowNum() + "行: 用户名 " + p.teacherNo()
                    + " 已被占用（可能是已删除账号），本次导入已全部回滚");
        }
    }

    // ---------------------------------------------------------------- 导出

    private TeacherExportExcel toExportRow(Teacher t, Map<Long, String> schoolNames,
                                           Map<Long, String> subjectNames, Map<Long, String> usernames) {
        return TeacherExportExcel.builder()
                .teacherNo(t.getTeacherNo())
                .name(t.getName())
                .gender(genderName(t.getGender()))
                .subjectName(t.getSubjectId() == null ? "" : subjectNames.getOrDefault(t.getSubjectId(), ""))
                .status(statusName(t.getStatus()))
                .username(t.getUserId() == null ? "" : usernames.getOrDefault(t.getUserId(), ""))
                .schoolName(t.getSchoolId() == null ? "" : schoolNames.getOrDefault(t.getSchoolId(), ""))
                .build();
    }

    private Map<Long, String> loadSchoolNames(List<Teacher> teachers) {
        Set<Long> schoolIds = teachers.stream().map(Teacher::getSchoolId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (schoolIds.isEmpty()) {
            return Map.of();
        }
        return schoolMapper.selectBatchIds(schoolIds).stream()
                .filter(s -> s.getSchoolName() != null)
                .collect(Collectors.toMap(School::getId, School::getSchoolName, (a, b) -> a));
    }

    private Map<Long, String> loadSubjectNames(List<Teacher> teachers) {
        Set<Long> subjectIds = teachers.stream().map(Teacher::getSubjectId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (subjectIds.isEmpty()) {
            return Map.of();
        }
        return subjectMapper.selectBatchIds(subjectIds).stream()
                .filter(s -> s.getSubjectName() != null)
                .collect(Collectors.toMap(Subject::getId, Subject::getSubjectName, (a, b) -> a));
    }

    private Map<Long, String> loadUsernames(List<Teacher> teachers) {
        Set<Long> userIds = teachers.stream().map(Teacher::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return sysUserMapper.selectBatchIds(userIds).stream()
                .filter(u -> u.getUsername() != null)
                .collect(Collectors.toMap(SysUser::getId, SysUser::getUsername, (a, b) -> a));
    }

    private String genderName(Integer gender) {
        if (gender == null) {
            return "";
        }
        return switch (gender) {
            case 1 -> "男";
            case 2 -> "女";
            default -> "";
        };
    }

    private String statusName(Integer status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case 0 -> "离职";
            case 1 -> "在职";
            default -> "";
        };
    }

}
