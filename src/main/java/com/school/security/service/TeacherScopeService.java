package com.school.security.service;

import com.school.module.teacher.entity.TeacherClass;
import com.school.module.teacher.mapper.TeacherClassMapper;
import com.school.security.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 教师数据范围服务
 * <p>
 * 封装"当前教师能看哪些班级/科目"的查询，供 ScoreService、StatService 等注入使用。
 * 不放在 SecurityUtils 里是因为 SecurityUtils 是静态工具类，无法依赖注入 Mapper。
 *
 * @author School System
 */
@Service
@RequiredArgsConstructor
public class TeacherScopeService {

    private final TeacherClassMapper teacherClassMapper;

    /**
     * 指定教师任教的班级ID集合（跨所有科目）
     * AI 工具层调用，teacherId 由 ToolContext 传入，不走 SecurityContextHolder
     */
    public Set<Long> teachingClassIdsFor(Long teacherId) {
        return teacherClassMapper.selectByTeacherId(teacherId).stream()
                .map(TeacherClass::getClassId)
                .collect(Collectors.toSet());
    }

    /**
     * 指定教师担任班主任的班级ID集合
     */
    public Set<Long> headClassIdsFor(Long teacherId) {
        return teacherClassMapper.selectHeadClassIdsByTeacherId(teacherId)
                .stream().collect(Collectors.toSet());
    }

    /**
     * 指定教师有权访问的全部班级ID（任课 ∪ 班主任）
     */
    public Set<Long> allowedClassIdsFor(Long teacherId) {
        Set<Long> result = teachingClassIdsFor(teacherId);
        result.addAll(headClassIdsFor(teacherId));
        return result;
    }

    /**
     * 当前教师任教的(classId, subjectId)组合
     */
    public List<TeacherClass> currentTeachingAssignments() {
        return teacherClassMapper.selectByTeacherId(SecurityUtils.currentTeacherId());
    }

    /**
     * 当前教师任教的班级ID集合（跨所有科目）
     */
    public Set<Long> currentTeachingClassIds() {
        return currentTeachingAssignments().stream()
                .map(TeacherClass::getClassId)
                .collect(Collectors.toSet());
    }

    /**
     * 当前教师担任班主任的班级ID集合
     */
    public Set<Long> currentHeadClassIds() {
        return teacherClassMapper
                .selectHeadClassIdsByTeacherId(SecurityUtils.currentTeacherId())
                .stream().collect(Collectors.toSet());
    }

    /**
     * 当前教师有权访问的全部班级ID
     * <p>
     * 规则：任教班级（仅限对应科目）∪ 担任班主任的班级（全科）
     * 此方法返回两者的并集，用于"该老师能进入哪个班级页"的粗粒度判断。
     * 细粒度（科目级别）见 {@link #isAllowedClassSubject(Long, Long)}。
     */
    public Set<Long> currentAllowedClassIds() {
        Set<Long> result = currentTeachingClassIds();
        result.addAll(currentHeadClassIds());
        return result;
    }

    /**
     * 判断当前教师是否有权访问指定班级的指定科目
     * <p>
     * 满足任一即可：
     * <ul>
     *   <li>该教师在 teacher_class 里有 (classId, subjectId) 记录（任课教师）</li>
     *   <li>该教师是该班的班主任（可查全科）</li>
     * </ul>
     */
    public boolean isAllowedClassSubject(Long classId, Long subjectId) {
        Long teacherId = SecurityUtils.currentTeacherId();
        List<TeacherClass> assignments = teacherClassMapper.selectByTeacherId(teacherId);
        boolean isSubjectTeacher = assignments.stream()
                .anyMatch(tc -> tc.getClassId().equals(classId) && tc.getSubjectId().equals(subjectId));
        if (isSubjectTeacher) return true;
        List<Long> headClassIds = teacherClassMapper.selectHeadClassIdsByTeacherId(teacherId);
        return headClassIds.contains(classId);
    }

}
