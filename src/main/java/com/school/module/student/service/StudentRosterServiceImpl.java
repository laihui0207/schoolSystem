package com.school.module.student.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.common.constant.CommonConstants;
import com.school.common.exception.BusinessException;
import com.school.module.class_module.entity.SchoolClass;
import com.school.module.class_module.mapper.ClassMapper;
import com.school.module.school.entity.School;
import com.school.module.school.mapper.SchoolMapper;
import com.school.module.student.dto.StudentClassRow;
import com.school.module.student.dto.StudentExportExcel;
import com.school.module.student.dto.StudentImportExcel;
import com.school.module.student.dto.StudentQueryRequest;
import com.school.module.student.entity.ClassStudent;
import com.school.module.student.entity.Student;
import com.school.module.student.mapper.ClassStudentMapper;
import com.school.module.student.mapper.StudentMapper;
import com.school.module.student.roster.RosterCells;
import com.school.module.student.roster.RosterExcel;
import com.school.module.student.roster.RosterImportResult;
import com.school.module.term.entity.AcademicTerm;
import com.school.module.term.mapper.AcademicTermMapper;
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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 学生档案批量导入导出实现
 *
 * @author School System
 */
@Service
@RequiredArgsConstructor
public class StudentRosterServiceImpl implements StudentRosterService {

    private final StudentMapper studentMapper;
    private final ClassStudentMapper classStudentMapper;
    // ponytail: 以下均为只读注入——按班级名定位本校班级、比较学期先后、回填学校名、预检用户名占用；不往对方模块加方法
    private final ClassMapper classMapper;
    private final AcademicTermMapper academicTermMapper;
    private final SchoolMapper schoolMapper;
    private final SysUserMapper sysUserMapper;
    // ponytail: 开户复用 UserService.createUser——用户名唯一、BCrypt、sys_user_role 绑定、非超管学校归属都在那里收口
    private final UserService userService;

    private static final String TEMPLATE_FILE = "学生导入模板";
    private static final String EXPORT_FILE_PREFIX = "学生档案导出_";
    private static final long EXPORT_MAX_ROWS = 50_000L;
    private static final int STUDENT_NO_MAX = 30;
    private static final int NAME_MAX = 50;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 校验通过、待落库的一行
     */
    private record PreparedStudent(int rowNum, String studentNo, String name, Integer gender,
                                   LocalDate birthDate, LocalDate enrollmentDate,
                                   SchoolClass schoolClass, boolean openAccount) {
    }

    /**
     * 整表预加载的参照数据
     */
    private record ImportContext(Map<String, Student> existingByNo,
                                 Map<String, SchoolClass> classByName,
                                 Set<String> takenUsernames,
                                 List<String> warnings) {
    }

    @Override
    public void downloadTemplate(HttpServletResponse response) {
        StudentImportExcel example = new StudentImportExcel();
        example.setStudentNo("20240001");
        example.setName("张三");
        example.setGender("男");
        example.setBirthDate("2010-09-01");
        example.setEnrollmentDate("2024-09-01");
        example.setClassName("一年级1班");
        example.setOpenAccount("是");
        RosterExcel.write(response, TEMPLATE_FILE, "学生导入", StudentImportExcel.class, List.of(example));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RosterImportResult importStudents(MultipartFile file, Long requestedSchoolId) {
        Long schoolId = SecurityUtils.resolveSchoolId(requestedSchoolId);
        List<StudentImportExcel> rows = RosterExcel.read(file, StudentImportExcel.class);
        ImportContext ctx = loadContext(schoolId, rows);

        List<PreparedStudent> prepared = new ArrayList<>();
        List<RosterImportResult.Failure> failures = new ArrayList<>();
        Set<String> seenNos = new HashSet<>();
        for (int i = 0; i < rows.size(); i++) {
            StudentImportExcel row = rows.get(i);
            if (row.isBlankRow()) {
                continue;
            }
            // 表头占第 1 行，数据行号从 2 开始
            int rowNum = i + 2;
            try {
                prepared.add(validateRow(rowNum, row, ctx, seenNos, schoolId));
            } catch (IllegalArgumentException e) {
                failures.add(RosterImportResult.Failure.builder()
                        .row(rowNum)
                        .studentNo(RosterCells.normalizeCode(row.getStudentNo()))
                        .reason(e.getMessage())
                        .build());
            }
        }

        if (prepared.isEmpty() && failures.isEmpty()) {
            throw new BusinessException("Excel 没有可导入的数据行");
        }
        if (!failures.isEmpty()) {
            // ponytail: 任一行无效就整批不落库——部分导入会留下半份名册，用户无法从结果判断该补哪些行
            return RosterImportResult.builder()
                    .successCount(0)
                    .failCount(failures.size())
                    .failures(failures)
                    .warnings(ctx.warnings())
                    .build();
        }

        List<RosterImportResult.CreatedAccount> accounts = new ArrayList<>();
        for (PreparedStudent p : prepared) {
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
    public void exportStudents(StudentQueryRequest request, HttpServletResponse response) {
        // ponytail: 导出含账号用户名，只给管理员；非超管强制本校（租户插件也会加 school_id，这里显式收口便于单测与阅读）
        if (!SecurityUtils.isSuperAdmin()) {
            request.setSchoolId(SecurityUtils.currentSchoolId());
        }
        // ponytail: 复用列表页同一条 SQL 保证「导出 = 当前筛选」；size=-1 + maxLimit 让分页插件只加一个 LIMIT 上限，不做 COUNT
        Page<Student> page = new Page<>(1, -1, false);
        page.setMaxLimit(EXPORT_MAX_ROWS);
        List<Student> students = studentMapper.selectPageByQuery(page, request).getRecords();
        if (students == null || students.isEmpty()) {
            throw new BusinessException("当前筛选条件下没有学生数据");
        }

        Set<Long> studentIds = students.stream().map(Student::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, StudentClassRow> firstClass = studentIds.isEmpty() ? Map.of()
                : classStudentMapper.selectClassRowsByStudentIds(studentIds).stream()
                        .collect(Collectors.toMap(StudentClassRow::getStudentId, r -> r, (a, b) -> a));
        Map<Long, String> schoolNames = loadSchoolNames(students);
        Map<Long, String> usernames = loadUsernames(students);

        List<StudentExportExcel> data = students.stream()
                .map(s -> toExportRow(s, firstClass.get(s.getId()), schoolNames, usernames))
                .collect(Collectors.toList());
        RosterExcel.write(response, EXPORT_FILE_PREFIX + System.currentTimeMillis(), "学生档案",
                StudentExportExcel.class, data);
    }

    // ---------------------------------------------------------------- 导入：预加载

    private ImportContext loadContext(Long schoolId, List<StudentImportExcel> rows) {
        List<String> nos = rows.stream()
                .map(r -> RosterCells.normalizeCode(r.getStudentNo()))
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        // ponytail: student_no 在 DDL（V2）里是全局 UNIQUE，这里不按学校过滤：超管能看到别校占用并给出明确原因；
        // 校管理员受租户插件限制只看得到本校行，别校占用由 persist 里的唯一索引兜底
        Map<String, Student> existingByNo = nos.isEmpty() ? Map.of()
                : studentMapper.selectList(new LambdaQueryWrapper<Student>().in(Student::getStudentNo, nos)).stream()
                        .collect(Collectors.toMap(Student::getStudentNo, s -> s, (a, b) -> a));

        // ponytail: 不用 .select(SysUser::getUsername) 投影——LambdaQueryWrapper.select 会立刻解析列名，
        // 纯 Mockito 单测里没有 TableInfo 缓存会直接抛异常；IN 列表 ≤ 2000 行，整行查回代价可忽略
        Set<String> takenUsernames = nos.isEmpty() ? Set.of()
                : sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .in(SysUser::getUsername, nos)).stream()
                        .map(SysUser::getUsername)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

        Set<String> classNames = rows.stream()
                .map(r -> RosterCells.trimToNull(r.getClassName()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<String> warnings = new ArrayList<>();
        Map<String, SchoolClass> classByName = resolveClasses(schoolId, classNames, warnings);

        return new ImportContext(existingByNo, classByName, takenUsernames, warnings);
    }

    /**
     * 按班级名定位本校班级；同名多条时取最新学期的那一个并提示
     */
    private Map<String, SchoolClass> resolveClasses(Long schoolId, Set<String> names, List<String> warnings) {
        if (names.isEmpty()) {
            return Map.of();
        }
        List<SchoolClass> classes = classMapper.selectList(new LambdaQueryWrapper<SchoolClass>()
                .eq(SchoolClass::getSchoolId, schoolId)
                .in(SchoolClass::getClassName, names));
        Map<String, List<SchoolClass>> grouped = classes.stream()
                .filter(c -> c.getClassName() != null)
                .collect(Collectors.groupingBy(SchoolClass::getClassName));

        boolean hasDuplicate = grouped.values().stream().anyMatch(list -> list.size() > 1);
        Map<Long, AcademicTerm> terms = hasDuplicate ? loadTerms(classes) : Map.of();

        Map<String, SchoolClass> result = new HashMap<>();
        grouped.forEach((name, list) -> {
            SchoolClass chosen = list.size() == 1 ? list.get(0) : pickLatest(list, terms);
            if (list.size() > 1) {
                warnings.add("班级「" + name + "」有 " + list.size() + " 个同名班级，已选用最新学期的一个（ID " + chosen.getId() + "）");
            }
            result.put(name, chosen);
        });
        return result;
    }

    /**
     * ponytail: 同名班级跨学期并存是常态（每年都有「一年级1班」），按学期 year/term 取最新；学期相同或缺失则取 id 最大
     */
    private SchoolClass pickLatest(List<SchoolClass> list, Map<Long, AcademicTerm> terms) {
        SchoolClass best = list.get(0);
        for (int i = 1; i < list.size(); i++) {
            if (compareByTerm(list.get(i), best, terms) > 0) {
                best = list.get(i);
            }
        }
        return best;
    }

    /**
     * 学年（"2024-2025" 字符串序即时间序）→ 学期序号 → 班级 id；缺失学期视为最早
     */
    private int compareByTerm(SchoolClass a, SchoolClass b, Map<Long, AcademicTerm> terms) {
        AcademicTerm ta = a.getTermId() == null ? null : terms.get(a.getTermId());
        AcademicTerm tb = b.getTermId() == null ? null : terms.get(b.getTermId());
        int byYear = compareNullsFirst(ta == null ? null : ta.getYear(), tb == null ? null : tb.getYear());
        if (byYear != 0) {
            return byYear;
        }
        int byTerm = compareNullsFirst(ta == null ? null : ta.getTerm(), tb == null ? null : tb.getTerm());
        if (byTerm != 0) {
            return byTerm;
        }
        return compareNullsFirst(a.getId(), b.getId());
    }

    private static <T extends Comparable<T>> int compareNullsFirst(T x, T y) {
        if (x == null) {
            return y == null ? 0 : -1;
        }
        return y == null ? 1 : x.compareTo(y);
    }

    private Map<Long, AcademicTerm> loadTerms(List<SchoolClass> classes) {
        Set<Long> termIds = classes.stream().map(SchoolClass::getTermId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (termIds.isEmpty()) {
            return Map.of();
        }
        return academicTermMapper.selectBatchIds(termIds).stream()
                .collect(Collectors.toMap(AcademicTerm::getId, t -> t, (a, b) -> a));
    }

    // ---------------------------------------------------------------- 导入：逐行校验

    private PreparedStudent validateRow(int rowNum, StudentImportExcel row, ImportContext ctx,
                                        Set<String> seenNos, Long schoolId) {
        String studentNo = RosterCells.normalizeCode(row.getStudentNo());
        if (studentNo == null) {
            throw new IllegalArgumentException("学号不能为空");
        }
        if (studentNo.length() > STUDENT_NO_MAX) {
            throw new IllegalArgumentException("学号不能超过 " + STUDENT_NO_MAX + " 个字符");
        }
        if (!seenNos.add(studentNo)) {
            throw new IllegalArgumentException("学号在文件内重复");
        }
        Student existing = ctx.existingByNo().get(studentNo);
        if (existing != null) {
            throw new IllegalArgumentException(schoolId.equals(existing.getSchoolId())
                    ? "学号已存在" : "学号已被其他学校使用（学号全局唯一）");
        }

        String name = RosterCells.trimToNull(row.getName());
        if (name == null) {
            throw new IllegalArgumentException("姓名不能为空");
        }
        if (name.length() > NAME_MAX) {
            throw new IllegalArgumentException("姓名不能超过 " + NAME_MAX + " 个字符");
        }

        Integer gender = RosterCells.parseGender(row.getGender());
        LocalDate birthDate = RosterCells.parseDate(row.getBirthDate(), "出生日期");
        LocalDate enrollmentDate = RosterCells.parseDate(row.getEnrollmentDate(), "入学日期");

        String className = RosterCells.trimToNull(row.getClassName());
        SchoolClass schoolClass = null;
        if (className != null) {
            schoolClass = ctx.classByName().get(className);
            if (schoolClass == null) {
                throw new IllegalArgumentException("班级「" + className + "」在本校不存在");
            }
        }

        boolean openAccount = RosterCells.parseYesNo(row.getOpenAccount(), "是否开通账号");
        if (openAccount && ctx.takenUsernames().contains(studentNo)) {
            throw new IllegalArgumentException("用户名 " + studentNo + " 已被占用，无法开通账号");
        }

        return new PreparedStudent(rowNum, studentNo, name, gender, birthDate, enrollmentDate, schoolClass, openAccount);
    }

    // ---------------------------------------------------------------- 导入：落库

    private void persist(PreparedStudent p, Long schoolId, List<RosterImportResult.CreatedAccount> accounts) {
        Long userId = null;
        if (p.openAccount()) {
            userId = createAccount(p, schoolId);
            accounts.add(RosterImportResult.CreatedAccount.builder()
                    .row(p.rowNum())
                    .username(p.studentNo())
                    .realName(p.name())
                    .initialPassword(RosterCells.DEFAULT_PASSWORD)
                    .build());
        }

        Student student = new Student();
        student.setStudentNo(p.studentNo());
        student.setSchoolId(schoolId);
        student.setName(p.name());
        student.setGender(p.gender());
        student.setBirthDate(p.birthDate());
        student.setEnrollmentDate(p.enrollmentDate());
        student.setUserId(userId);
        student.setStatus(1);
        try {
            studentMapper.insert(student);
        } catch (DuplicateKeyException e) {
            // ponytail: 校管理员看不到别校的同学号行（租户过滤），只能靠唯一索引兜底；带行号抛出，事务整批回滚
            throw new BusinessException("第" + p.rowNum() + "行: 学号 " + p.studentNo()
                    + " 已被占用（学号全局唯一），本次导入已全部回滚");
        }

        if (p.schoolClass() != null) {
            ClassStudent classStudent = new ClassStudent();
            classStudent.setClassId(p.schoolClass().getId());
            classStudent.setStudentId(student.getId());
            classStudentMapper.insert(classStudent);
        }
    }

    private Long createAccount(PreparedStudent p, Long schoolId) {
        UserCreateRequest request = new UserCreateRequest();
        request.setUsername(p.studentNo());
        request.setPassword(RosterCells.DEFAULT_PASSWORD);
        request.setRealName(p.name());
        request.setRole(UserRole.STUDENT.getCode());
        request.setSchoolId(schoolId);
        request.setStatus(CommonConstants.STATUS_ENABLED);
        try {
            UserResponse created = userService.createUser(request);
            return created.getId();
        } catch (BusinessException e) {
            // 预检已排除用户名占用，这里兜住并发等极端情况，补上行号后整批回滚
            throw new BusinessException(e.getCode(), "第" + p.rowNum() + "行: 开通账号失败——" + e.getMessage());
        } catch (DuplicateKeyException e) {
            // ponytail: 预检只看 deleted=0 的用户，被逻辑删除的同名账号仍占着 username 唯一索引
            throw new BusinessException("第" + p.rowNum() + "行: 用户名 " + p.studentNo()
                    + " 已被占用（可能是已删除账号），本次导入已全部回滚");
        }
    }

    // ---------------------------------------------------------------- 导出

    private StudentExportExcel toExportRow(Student s, StudentClassRow classRow,
                                           Map<Long, String> schoolNames, Map<Long, String> usernames) {
        return StudentExportExcel.builder()
                .studentNo(s.getStudentNo())
                .name(s.getName())
                .gender(genderName(s.getGender()))
                .birthDate(s.getBirthDate() == null ? "" : DATE_FMT.format(s.getBirthDate()))
                .enrollmentDate(s.getEnrollmentDate() == null ? "" : DATE_FMT.format(s.getEnrollmentDate()))
                .gradeName(classRow == null || classRow.getGradeName() == null ? "" : classRow.getGradeName())
                .className(classRow == null || classRow.getClassName() == null ? "" : classRow.getClassName())
                .status(statusName(s.getStatus()))
                .username(s.getUserId() == null ? "" : usernames.getOrDefault(s.getUserId(), ""))
                .schoolName(s.getSchoolId() == null ? "" : schoolNames.getOrDefault(s.getSchoolId(), ""))
                .build();
    }

    private Map<Long, String> loadSchoolNames(List<Student> students) {
        Set<Long> schoolIds = students.stream().map(Student::getSchoolId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (schoolIds.isEmpty()) {
            return Map.of();
        }
        return schoolMapper.selectBatchIds(schoolIds).stream()
                .filter(s -> s.getSchoolName() != null)
                .collect(Collectors.toMap(School::getId, School::getSchoolName, (a, b) -> a));
    }

    private Map<Long, String> loadUsernames(List<Student> students) {
        Set<Long> userIds = students.stream().map(Student::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());
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
            case 0 -> "离校";
            case 1 -> "在读";
            case 2 -> "休学";
            default -> "";
        };
    }

}
