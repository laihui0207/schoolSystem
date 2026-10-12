package com.school.module.teacher.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.common.exception.BusinessException;
import com.school.module.class_module.entity.SchoolClass;
import com.school.module.class_module.mapper.ClassMapper;
import com.school.module.school.entity.School;
import com.school.module.school.mapper.SchoolMapper;
import com.school.module.subject.entity.Subject;
import com.school.module.subject.mapper.SubjectMapper;
import com.school.module.teacher.dto.TeacherCreateRequest;
import com.school.module.teacher.dto.TeacherQueryRequest;
import com.school.module.teacher.dto.TeacherResponse;
import com.school.module.teacher.dto.TeacherUpdateRequest;
import com.school.module.teacher.entity.Teacher;
import com.school.module.teacher.entity.TeacherClass;
import com.school.module.teacher.mapper.TeacherClassMapper;
import com.school.module.teacher.mapper.TeacherMapper;
import com.school.module.user.entity.SysUser;
import com.school.module.user.enums.UserRole;
import com.school.module.user.mapper.SysUserMapper;
import com.school.security.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeacherServiceImpl implements TeacherService {

    private final TeacherMapper teacherMapper;
    private final TeacherClassMapper teacherClassMapper;
    // ponytail: 只读注入做名称回填与存在性/同校校验，不往对方模块的 Mapper/Service 里加方法
    private final SchoolMapper schoolMapper;
    private final SubjectMapper subjectMapper;
    private final ClassMapper classMapper;
    private final SysUserMapper sysUserMapper;

    /**
     * 解析更新后的登录账号绑定：null 保持不变，0 解绑，其他值必须是本校教师类型且未绑定别的档案的账号。
     */
    private Long resolveBoundUserId(Teacher existing, Long requested) {
        if (requested == null) {
            return existing.getUserId();
        }
        if (requested == 0L) {
            return null;
        }
        if (requested.equals(existing.getUserId())) {
            return requested;
        }
        SysUser user = sysUserMapper.selectById(requested);
        if (user == null) {
            throw new BusinessException("登录账号不存在");
        }
        if (!UserRole.TEACHER.getUserType().equals(user.getUserType())) {
            throw new BusinessException("只能绑定教师类型的登录账号");
        }
        if (user.getSchoolId() != null && !user.getSchoolId().equals(existing.getSchoolId())) {
            throw new BusinessException("登录账号与教师档案不属于同一学校");
        }
        Long occupied = teacherMapper.selectCount(new LambdaQueryWrapper<Teacher>()
                .eq(Teacher::getUserId, requested)
                .ne(Teacher::getId, existing.getId()));
        if (occupied > 0) {
            throw new BusinessException("该登录账号已绑定其他教师档案");
        }
        return requested;
    }

    private static final List<Integer> VALID_STATUS = Arrays.asList(0, 1);
    private static final List<Integer> VALID_GENDER = Arrays.asList(1, 2);

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TeacherResponse createTeacher(TeacherCreateRequest request) {
        if (teacherMapper.selectByTeacherNo(request.getTeacherNo()) != null) {
            throw new BusinessException("工号已存在");
        }
        if (request.getGender() != null && !VALID_GENDER.contains(request.getGender())) {
            throw new BusinessException("性别值无效，只能是1（男）或2（女）");
        }
        Teacher teacher = new Teacher();
        teacher.setTeacherNo(request.getTeacherNo());
        teacher.setSchoolId(SecurityUtils.resolveSchoolId(request.getSchoolId()));
        teacher.setName(request.getName());
        teacher.setGender(request.getGender());
        teacher.setSubjectId(request.getSubjectId());
        teacher.setUserId(request.getUserId());
        teacher.setStatus(1);
        teacherMapper.insert(teacher);
        return convertToResponse(teacher);
    }

    @Override
    public TeacherResponse getTeacherById(Long id) {
        Teacher teacher = teacherMapper.selectById(id);
        if (teacher == null) throw new BusinessException("教师不存在");
        // ponytail: 走批量回填路径，详情页同样拿到 schoolName/subjectName，而不只是列表页
        return convertToResponses(List.of(teacher)).get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TeacherResponse updateTeacher(Long id, TeacherUpdateRequest request) {
        Teacher existing = teacherMapper.selectById(id);
        if (existing == null) throw new BusinessException("教师不存在");
        if (request.getGender() != null && !VALID_GENDER.contains(request.getGender())) {
            throw new BusinessException("性别值无效，只能是1（男）或2（女）");
        }
        if (request.getStatus() != null && !VALID_STATUS.contains(request.getStatus())) {
            throw new BusinessException("状态值无效，只能是0（离职）或1（在职）");
        }
        Teacher updated = new Teacher();
        updated.setId(existing.getId());
        updated.setTeacherNo(existing.getTeacherNo());
        updated.setSchoolId(existing.getSchoolId());
        updated.setUserId(resolveBoundUserId(existing, request.getUserId()));
        updated.setName(request.getName() != null ? request.getName() : existing.getName());
        updated.setGender(request.getGender() != null ? request.getGender() : existing.getGender());
        updated.setSubjectId(request.getSubjectId() != null ? request.getSubjectId() : existing.getSubjectId());
        updated.setStatus(request.getStatus() != null ? request.getStatus() : existing.getStatus());
        teacherMapper.updateById(updated);
        return convertToResponse(updated);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTeacherStatus(Long id, Integer status) {
        if (!VALID_STATUS.contains(status)) throw new BusinessException("状态值无效，只能是0（离职）或1（在职）");
        Teacher existing = teacherMapper.selectById(id);
        if (existing == null) throw new BusinessException("教师不存在");
        Teacher updated = new Teacher();
        updated.setId(existing.getId());
        updated.setTeacherNo(existing.getTeacherNo());
        updated.setSchoolId(existing.getSchoolId());
        updated.setUserId(existing.getUserId());
        updated.setName(existing.getName());
        updated.setGender(existing.getGender());
        updated.setSubjectId(existing.getSubjectId());
        updated.setStatus(status);
        teacherMapper.updateById(updated);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setSubject(Long id, Long subjectId) {
        Teacher existing = teacherMapper.selectById(id);
        if (existing == null) throw new BusinessException("教师不存在");
        Teacher updated = new Teacher();
        updated.setId(existing.getId());
        updated.setTeacherNo(existing.getTeacherNo());
        updated.setSchoolId(existing.getSchoolId());
        updated.setUserId(existing.getUserId());
        updated.setName(existing.getName());
        updated.setGender(existing.getGender());
        updated.setSubjectId(subjectId);
        updated.setStatus(existing.getStatus());
        teacherMapper.updateById(updated);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTeacher(Long id) {
        if (teacherMapper.selectById(id) == null) throw new BusinessException("教师不存在");
        teacherMapper.deleteById(id);
    }

    @Override
    public Page<TeacherResponse> listTeachers(TeacherQueryRequest request) {
        Page<Teacher> page = new Page<>(request.getPageNum(), request.getPageSize());
        Page<Teacher> resultPage = teacherMapper.selectPageByQuery(page, request);
        Page<TeacherResponse> responsePage = new Page<>(resultPage.getCurrent(), resultPage.getSize(), resultPage.getTotal());
        responsePage.setRecords(convertToResponses(resultPage.getRecords()));
        return responsePage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignClass(Long teacherId, Long classId, Long subjectId) {
        Teacher teacher = teacherMapper.selectById(teacherId);
        if (teacher == null) throw new BusinessException("教师不存在");
        // ponytail: 没有外键约束，班级/科目必须存在且与教师同校，否则 teacher_class 会出现跨校或悬空行
        SchoolClass schoolClass = classMapper.selectById(classId);
        if (schoolClass == null) throw new BusinessException("班级不存在");
        if (teacher.getSchoolId() != null && !teacher.getSchoolId().equals(schoolClass.getSchoolId())) {
            throw new BusinessException("班级与教师不属于同一学校");
        }
        Subject subject = subjectMapper.selectById(subjectId);
        if (subject == null) throw new BusinessException("科目不存在");
        if (teacher.getSchoolId() != null && !teacher.getSchoolId().equals(subject.getSchoolId())) {
            throw new BusinessException("科目与教师不属于同一学校");
        }
        if (teacherClassMapper.countByTeacherClassSubject(teacherId, classId, subjectId) > 0) return; // 幂等
        TeacherClass tc = new TeacherClass();
        tc.setTeacherId(teacherId);
        tc.setClassId(classId);
        tc.setSubjectId(subjectId);
        teacherClassMapper.insert(tc);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeClassAssignment(Long teacherId, Long classId, Long subjectId) {
        // ponytail: G-8 — teacher_class 不在租户表白名单，先经租户过滤的 selectById 确认教师存在且同校，否则校管理员能删别校的任课分配
        if (teacherMapper.selectById(teacherId) == null) throw new BusinessException("教师不存在");
        int deleted = teacherClassMapper.deleteByTeacherClassSubject(teacherId, classId, subjectId);
        if (deleted == 0) throw new BusinessException("该分配记录不存在");
    }

    @Override
    public List<TeacherResponse.ClassAssignment> getClassAssignments(Long teacherId) {
        return teacherClassMapper.selectDetailByTeacherId(teacherId).stream()
                .map(this::toAssignment)
                .collect(Collectors.toList());
    }

    /**
     * 单条转换（创建/更新返回用）：不回填学校名、科目名、班级
     */
    private TeacherResponse convertToResponse(Teacher teacher) {
        return toResponse(teacher, null, null, List.of(), Boolean.FALSE);
    }

    /**
     * 批量转换：学校名、科目名、任课班级、是否班主任各用一条查询回填，避免 N+1
     */
    // ponytail: Teacher.schoolName/subjectName 是 @TableField(exist=false)，分页 SQL 从不赋值，
    // 之前直接搬这两个字段导致列表两列恒空；这里按 id 批量查名称再组装
    private List<TeacherResponse> convertToResponses(List<Teacher> teachers) {
        if (teachers == null || teachers.isEmpty()) {
            return List.of();
        }
        Map<Long, String> schoolNames = loadSchoolNames(teachers);
        Map<Long, String> subjectNames = loadSubjectNames(teachers);
        Set<Long> teacherIds = teachers.stream()
                .map(Teacher::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, List<TeacherResponse.ClassAssignment>> classesByTeacher = loadClassAssignments(teacherIds);
        Set<Long> headTeacherIds = loadHeadTeacherIds(teacherIds);

        return teachers.stream()
                .map(t -> toResponse(
                        t,
                        t.getSchoolId() == null ? null : schoolNames.get(t.getSchoolId()),
                        t.getSubjectId() == null ? null : subjectNames.get(t.getSubjectId()),
                        t.getId() == null ? List.of() : classesByTeacher.getOrDefault(t.getId(), List.of()),
                        t.getId() != null && headTeacherIds.contains(t.getId())))
                .collect(Collectors.toList());
    }

    private TeacherResponse toResponse(Teacher teacher,
                                       String schoolName,
                                       String subjectName,
                                       List<TeacherResponse.ClassAssignment> classes,
                                       Boolean headTeacher) {
        return TeacherResponse.builder()
                .id(teacher.getId())
                .userId(teacher.getUserId())
                .teacherNo(teacher.getTeacherNo())
                .schoolId(teacher.getSchoolId())
                .schoolName(schoolName)
                .name(teacher.getName())
                .gender(teacher.getGender())
                .genderName(getGenderName(teacher.getGender()))
                .subjectId(teacher.getSubjectId())
                .subjectName(subjectName)
                .status(teacher.getStatus())
                .statusName(getStatusName(teacher.getStatus()))
                .headTeacher(headTeacher)
                .classes(classes)
                .createdAt(teacher.getCreatedAt())
                .updatedAt(teacher.getUpdatedAt())
                .build();
    }

    private TeacherResponse.ClassAssignment toAssignment(TeacherClassMapper.TeacherClassDetail d) {
        return TeacherResponse.ClassAssignment.builder()
                .classId(d.getClassId())
                .className(d.getClassName())
                .subjectId(d.getSubjectId())
                .subjectName(d.getSubjectName())
                .isHeadTeacher(Boolean.TRUE.equals(d.getIsHeadTeacher()))
                .build();
    }

    private Map<Long, String> loadSchoolNames(List<Teacher> teachers) {
        Set<Long> schoolIds = teachers.stream()
                .map(Teacher::getSchoolId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (schoolIds.isEmpty()) {
            return Map.of();
        }
        return schoolMapper.selectBatchIds(schoolIds).stream()
                .filter(s -> s.getSchoolName() != null)
                .collect(Collectors.toMap(School::getId, School::getSchoolName, (a, b) -> a));
    }

    private Map<Long, String> loadSubjectNames(List<Teacher> teachers) {
        Set<Long> subjectIds = teachers.stream()
                .map(Teacher::getSubjectId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (subjectIds.isEmpty()) {
            return Map.of();
        }
        return subjectMapper.selectBatchIds(subjectIds).stream()
                .filter(s -> s.getSubjectName() != null)
                .collect(Collectors.toMap(Subject::getId, Subject::getSubjectName, (a, b) -> a));
    }

    private Map<Long, List<TeacherResponse.ClassAssignment>> loadClassAssignments(Set<Long> teacherIds) {
        if (teacherIds.isEmpty()) {
            return Map.of();
        }
        return teacherClassMapper.selectDetailByTeacherIds(teacherIds).stream()
                .filter(d -> d.getTeacherId() != null)
                .collect(Collectors.groupingBy(
                        TeacherClassMapper.TeacherClassDetail::getTeacherId,
                        Collectors.mapping(this::toAssignment, Collectors.toList())));
    }

    /**
     * 班主任身份来自 class.head_teacher_id，与 teacher_class 无关，所以单独查一次
     */
    private Set<Long> loadHeadTeacherIds(Set<Long> teacherIds) {
        if (teacherIds.isEmpty()) {
            return Set.of();
        }
        return classMapper.selectList(new LambdaQueryWrapper<SchoolClass>()
                        .select(SchoolClass::getHeadTeacherId)
                        .in(SchoolClass::getHeadTeacherId, teacherIds)).stream()
                .map(SchoolClass::getHeadTeacherId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private String getGenderName(Integer gender) {
        if (gender == null) return null;
        return switch (gender) { case 1 -> "男"; case 2 -> "女"; default -> "未知"; };
    }

    private String getStatusName(Integer status) {
        if (status == null) return null;
        return switch (status) { case 0 -> "离职"; case 1 -> "在职"; default -> "未知"; };
    }
}
