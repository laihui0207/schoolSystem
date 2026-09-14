package com.school.module.teacher.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.common.exception.BusinessException;
import com.school.module.teacher.dto.TeacherCreateRequest;
import com.school.module.teacher.dto.TeacherQueryRequest;
import com.school.module.teacher.dto.TeacherResponse;
import com.school.module.teacher.dto.TeacherUpdateRequest;
import com.school.module.teacher.entity.Teacher;
import com.school.module.teacher.mapper.TeacherMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 教师服务实现类
 *
 * @author School System
 */
@Service
@RequiredArgsConstructor
public class TeacherServiceImpl implements TeacherService {

    private final TeacherMapper teacherMapper;

    private static final List<Integer> VALID_STATUS = Arrays.asList(0, 1);
    private static final List<Integer> VALID_GENDER = Arrays.asList(1, 2);

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TeacherResponse createTeacher(TeacherCreateRequest request) {
        // 校验工号是否已存在
        Teacher existingTeacher = teacherMapper.selectByTeacherNo(request.getTeacherNo());
        if (existingTeacher != null) {
            throw new BusinessException("工号已存在");
        }

        // 校验性别
        if (request.getGender() != null && !VALID_GENDER.contains(request.getGender())) {
            throw new BusinessException("性别值无效，只能是1（男）或2（女）");
        }

        // 构建教师实体
        Teacher teacher = new Teacher();
        teacher.setTeacherNo(request.getTeacherNo());
        teacher.setSchoolId(request.getSchoolId());
        teacher.setName(request.getName());
        teacher.setGender(request.getGender());
        teacher.setSubjectId(request.getSubjectId());
        teacher.setUserId(request.getUserId());
        teacher.setStatus(1); // 默认在职

        // 保存到数据库
        teacherMapper.insert(teacher);

        return convertToResponse(teacher);
    }

    @Override
    public TeacherResponse getTeacherById(Long id) {
        Teacher teacher = teacherMapper.selectById(id);
        if (teacher == null) {
            throw new BusinessException("教师不存在");
        }
        return convertToResponse(teacher);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TeacherResponse updateTeacher(Long id, TeacherUpdateRequest request) {
        // 查询教师是否存在
        Teacher existingTeacher = teacherMapper.selectById(id);
        if (existingTeacher == null) {
            throw new BusinessException("教师不存在");
        }

        // 校验性别
        if (request.getGender() != null && !VALID_GENDER.contains(request.getGender())) {
            throw new BusinessException("性别值无效，只能是1（男）或2（女）");
        }

        // 校验状态
        if (request.getStatus() != null && !VALID_STATUS.contains(request.getStatus())) {
            throw new BusinessException("状态值无效，只能是0（离职）或1（在职）");
        }

        // 构建更新对象
        Teacher updatedTeacher = new Teacher();
        updatedTeacher.setId(existingTeacher.getId());
        updatedTeacher.setTeacherNo(existingTeacher.getTeacherNo());
        updatedTeacher.setSchoolId(existingTeacher.getSchoolId());
        updatedTeacher.setUserId(existingTeacher.getUserId());
        updatedTeacher.setName(request.getName() != null ? request.getName() : existingTeacher.getName());
        updatedTeacher.setGender(request.getGender() != null ? request.getGender() : existingTeacher.getGender());
        updatedTeacher.setSubjectId(request.getSubjectId() != null ? request.getSubjectId() : existingTeacher.getSubjectId());
        updatedTeacher.setStatus(request.getStatus() != null ? request.getStatus() : existingTeacher.getStatus());

        // 更新数据库
        teacherMapper.updateById(updatedTeacher);

        return convertToResponse(updatedTeacher);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTeacherStatus(Long id, Integer status) {
        // 校验状态值
        if (!VALID_STATUS.contains(status)) {
            throw new BusinessException("状态值无效，只能是0（离职）或1（在职）");
        }

        // 查询教师是否存在
        Teacher existingTeacher = teacherMapper.selectById(id);
        if (existingTeacher == null) {
            throw new BusinessException("教师不存在");
        }

        // 构建更新对象
        Teacher updatedTeacher = new Teacher();
        updatedTeacher.setId(existingTeacher.getId());
        updatedTeacher.setTeacherNo(existingTeacher.getTeacherNo());
        updatedTeacher.setSchoolId(existingTeacher.getSchoolId());
        updatedTeacher.setUserId(existingTeacher.getUserId());
        updatedTeacher.setName(existingTeacher.getName());
        updatedTeacher.setGender(existingTeacher.getGender());
        updatedTeacher.setSubjectId(existingTeacher.getSubjectId());
        updatedTeacher.setStatus(status);

        // 更新数据库
        teacherMapper.updateById(updatedTeacher);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setSubject(Long id, Long subjectId) {
        // 查询教师是否存在
        Teacher existingTeacher = teacherMapper.selectById(id);
        if (existingTeacher == null) {
            throw new BusinessException("教师不存在");
        }

        // 构建更新对象
        Teacher updatedTeacher = new Teacher();
        updatedTeacher.setId(existingTeacher.getId());
        updatedTeacher.setTeacherNo(existingTeacher.getTeacherNo());
        updatedTeacher.setSchoolId(existingTeacher.getSchoolId());
        updatedTeacher.setUserId(existingTeacher.getUserId());
        updatedTeacher.setName(existingTeacher.getName());
        updatedTeacher.setGender(existingTeacher.getGender());
        updatedTeacher.setSubjectId(subjectId);
        updatedTeacher.setStatus(existingTeacher.getStatus());

        // 更新数据库
        teacherMapper.updateById(updatedTeacher);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTeacher(Long id) {
        // 查询教师是否存在
        Teacher existingTeacher = teacherMapper.selectById(id);
        if (existingTeacher == null) {
            throw new BusinessException("教师不存在");
        }

        // 逻辑删除教师
        teacherMapper.deleteById(id);
    }

    @Override
    public Page<TeacherResponse> listTeachers(TeacherQueryRequest request) {
        // 构建分页对象
        Page<Teacher> page = new Page<>(request.getPageNum(), request.getPageSize());

        // 分页查询
        Page<Teacher> resultPage = teacherMapper.selectPageByQuery(page, request);

        // 转换为响应DTO
        Page<TeacherResponse> responsePage = new Page<>(
                resultPage.getCurrent(),
                resultPage.getSize(),
                resultPage.getTotal()
        );

        List<TeacherResponse> responses = resultPage.getRecords().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());

        responsePage.setRecords(responses);

        return responsePage;
    }

    /**
     * 转换为响应DTO
     */
    private TeacherResponse convertToResponse(Teacher teacher) {
        return TeacherResponse.builder()
                .id(teacher.getId())
                .userId(teacher.getUserId())
                .teacherNo(teacher.getTeacherNo())
                .schoolId(teacher.getSchoolId())
                .name(teacher.getName())
                .gender(teacher.getGender())
                .genderName(getGenderName(teacher.getGender()))
                .subjectId(teacher.getSubjectId())
                .status(teacher.getStatus())
                .statusName(getStatusName(teacher.getStatus()))
                .createdAt(teacher.getCreatedAt())
                .updatedAt(teacher.getUpdatedAt())
                .build();
    }

    /**
     * 获取性别名称
     */
    private String getGenderName(Integer gender) {
        if (gender == null) {
            return null;
        }
        return switch (gender) {
            case 1 -> "男";
            case 2 -> "女";
            default -> "未知";
        };
    }

    /**
     * 获取状态名称
     */
    private String getStatusName(Integer status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case 0 -> "离职";
            case 1 -> "在职";
            default -> "未知";
        };
    }

}
