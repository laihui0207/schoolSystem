package com.school.module.teacher.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.module.teacher.dto.TeacherCreateRequest;
import com.school.module.teacher.dto.TeacherQueryRequest;
import com.school.module.teacher.dto.TeacherResponse;
import com.school.module.teacher.dto.TeacherUpdateRequest;

import java.util.List;

public interface TeacherService {

    TeacherResponse createTeacher(TeacherCreateRequest request);

    TeacherResponse getTeacherById(Long id);

    TeacherResponse updateTeacher(Long id, TeacherUpdateRequest request);

    void updateTeacherStatus(Long id, Integer status);

    void setSubject(Long id, Long subjectId);

    void deleteTeacher(Long id);

    Page<TeacherResponse> listTeachers(TeacherQueryRequest request);

    /** 为教师分配班级+科目（任课），幂等 */
    void assignClass(Long teacherId, Long classId, Long subjectId);

    /** 移除教师的某条班级+科目分配 */
    void removeClassAssignment(Long teacherId, Long classId, Long subjectId);

    /** 查询教师任教班级列表（含是否班主任） */
    List<TeacherResponse.ClassAssignment> getClassAssignments(Long teacherId);
}

