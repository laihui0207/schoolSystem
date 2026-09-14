package com.school.module.student.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.common.exception.BusinessException;
import com.school.module.student.dto.StudentCreateRequest;
import com.school.module.student.dto.StudentQueryRequest;
import com.school.module.student.dto.StudentResponse;
import com.school.module.student.dto.StudentUpdateRequest;
import com.school.module.student.entity.ClassStudent;
import com.school.module.student.entity.Student;
import com.school.module.student.mapper.ClassStudentMapper;
import com.school.module.student.mapper.StudentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 学生服务实现类
 *
 * @author School System
 */
@Service
@RequiredArgsConstructor
public class StudentServiceImpl implements StudentService {

    private final StudentMapper studentMapper;
    private final ClassStudentMapper classStudentMapper;

    private static final List<Integer> VALID_STATUS = Arrays.asList(0, 1, 2);
    private static final List<Integer> VALID_GENDER = Arrays.asList(1, 2);

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StudentResponse createStudent(StudentCreateRequest request) {
        // 校验学号是否已存在
        Student existingStudent = studentMapper.selectByStudentNo(request.getStudentNo());
        if (existingStudent != null) {
            throw new BusinessException("学号已存在");
        }

        // 校验性别
        if (request.getGender() != null && !VALID_GENDER.contains(request.getGender())) {
            throw new BusinessException("性别值无效，只能是1（男）或2（女）");
        }

        // 构建学生实体
        Student student = new Student();
        student.setStudentNo(request.getStudentNo());
        student.setSchoolId(request.getSchoolId());
        student.setName(request.getName());
        student.setGender(request.getGender());
        student.setBirthDate(request.getBirthDate());
        student.setEnrollmentDate(request.getEnrollmentDate());
        student.setUserId(request.getUserId());
        student.setStatus(1); // 默认在读

        // 保存到数据库
        studentMapper.insert(student);

        return convertToResponse(student);
    }

    @Override
    public StudentResponse getStudentById(Long id) {
        Student student = studentMapper.selectById(id);
        if (student == null) {
            throw new BusinessException("学生不存在");
        }
        return convertToResponse(student);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StudentResponse updateStudent(Long id, StudentUpdateRequest request) {
        // 查询学生是否存在
        Student existingStudent = studentMapper.selectById(id);
        if (existingStudent == null) {
            throw new BusinessException("学生不存在");
        }

        // 校验性别
        if (request.getGender() != null && !VALID_GENDER.contains(request.getGender())) {
            throw new BusinessException("性别值无效，只能是1（男）或2（女）");
        }

        // 校验状态
        if (request.getStatus() != null && !VALID_STATUS.contains(request.getStatus())) {
            throw new BusinessException("状态值无效，只能是0（离校）、1（在读）或2（休学）");
        }

        // 构建更新对象
        Student updatedStudent = new Student();
        updatedStudent.setId(existingStudent.getId());
        updatedStudent.setStudentNo(existingStudent.getStudentNo());
        updatedStudent.setSchoolId(existingStudent.getSchoolId());
        updatedStudent.setUserId(existingStudent.getUserId());
        updatedStudent.setName(request.getName() != null ? request.getName() : existingStudent.getName());
        updatedStudent.setGender(request.getGender() != null ? request.getGender() : existingStudent.getGender());
        updatedStudent.setBirthDate(request.getBirthDate() != null ? request.getBirthDate() : existingStudent.getBirthDate());
        updatedStudent.setEnrollmentDate(request.getEnrollmentDate() != null ? request.getEnrollmentDate() : existingStudent.getEnrollmentDate());
        updatedStudent.setStatus(request.getStatus() != null ? request.getStatus() : existingStudent.getStatus());

        // 更新数据库
        studentMapper.updateById(updatedStudent);

        return convertToResponse(updatedStudent);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStudentStatus(Long id, Integer status) {
        // 校验状态值
        if (!VALID_STATUS.contains(status)) {
            throw new BusinessException("状态值无效，只能是0（离校）、1（在读）或2（休学）");
        }

        // 查询学生是否存在
        Student existingStudent = studentMapper.selectById(id);
        if (existingStudent == null) {
            throw new BusinessException("学生不存在");
        }

        // 构建更新对象
        Student updatedStudent = new Student();
        updatedStudent.setId(existingStudent.getId());
        updatedStudent.setStudentNo(existingStudent.getStudentNo());
        updatedStudent.setSchoolId(existingStudent.getSchoolId());
        updatedStudent.setUserId(existingStudent.getUserId());
        updatedStudent.setName(existingStudent.getName());
        updatedStudent.setGender(existingStudent.getGender());
        updatedStudent.setBirthDate(existingStudent.getBirthDate());
        updatedStudent.setEnrollmentDate(existingStudent.getEnrollmentDate());
        updatedStudent.setStatus(status);

        // 更新数据库
        studentMapper.updateById(updatedStudent);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteStudent(Long id) {
        // 查询学生是否存在
        Student existingStudent = studentMapper.selectById(id);
        if (existingStudent == null) {
            throw new BusinessException("学生不存在");
        }

        // 删除班级关联
        classStudentMapper.deleteByStudentId(id);

        // 逻辑删除学生
        studentMapper.deleteById(id);
    }

    @Override
    public Page<StudentResponse> listStudents(StudentQueryRequest request) {
        // 构建分页对象
        Page<Student> page = new Page<>(request.getPageNum(), request.getPageSize());

        // 分页查询
        Page<Student> resultPage = studentMapper.selectPageByQuery(page, request);

        // 转换为响应DTO
        Page<StudentResponse> responsePage = new Page<>(
                resultPage.getCurrent(),
                resultPage.getSize(),
                resultPage.getTotal()
        );

        List<StudentResponse> responses = resultPage.getRecords().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());

        responsePage.setRecords(responses);

        return responsePage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignToClass(Long studentId, Long classId) {
        // 查询学生是否存在
        Student student = studentMapper.selectById(studentId);
        if (student == null) {
            throw new BusinessException("学生不存在");
        }

        // 检查是否已经在该班级
        List<Long> classIds = classStudentMapper.selectClassIdsByStudentId(studentId);
        if (classIds.contains(classId)) {
            throw new BusinessException("学生已在该班级中");
        }

        // 创建班级学生关联
        ClassStudent classStudent = new ClassStudent();
        classStudent.setClassId(classId);
        classStudent.setStudentId(studentId);

        classStudentMapper.insert(classStudent);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transferClass(Long studentId, Long fromClassId, Long toClassId) {
        // 查询学生是否存在
        Student student = studentMapper.selectById(studentId);
        if (student == null) {
            throw new BusinessException("学生不存在");
        }

        // 检查学生是否在原班级
        List<Long> classIds = classStudentMapper.selectClassIdsByStudentId(studentId);
        if (!classIds.contains(fromClassId)) {
            throw new BusinessException("学生不在原班级中");
        }

        // 检查目标班级是否已存在该学生
        if (classIds.contains(toClassId)) {
            throw new BusinessException("学生已在目标班级中");
        }

        // 删除原班级关联
        classStudentMapper.deleteByStudentIdAndClassId(studentId, fromClassId);

        // 添加新班级关联
        ClassStudent classStudent = new ClassStudent();
        classStudent.setClassId(toClassId);
        classStudent.setStudentId(studentId);
        classStudentMapper.insert(classStudent);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeFromClass(Long studentId, Long classId) {
        // 查询学生是否存在
        Student student = studentMapper.selectById(studentId);
        if (student == null) {
            throw new BusinessException("学生不存在");
        }

        // 删除班级关联
        int deleted = classStudentMapper.deleteByStudentIdAndClassId(studentId, classId);
        if (deleted == 0) {
            throw new BusinessException("学生不在该班级中");
        }
    }

    @Override
    public List<StudentResponse> getStudentsByClassId(Long classId) {
        // 查询班级下的所有学生ID
        List<Long> studentIds = classStudentMapper.selectStudentIdsByClassId(classId);

        if (studentIds.isEmpty()) {
            return List.of();
        }

        // 批量查询学生信息
        List<Student> students = studentIds.stream()
                .map(studentMapper::selectById)
                .filter(student -> student != null && student.getDeleted() == 0)
                .collect(Collectors.toList());

        return students.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * 转换为响应DTO
     */
    private StudentResponse convertToResponse(Student student) {
        return StudentResponse.builder()
                .id(student.getId())
                .userId(student.getUserId())
                .studentNo(student.getStudentNo())
                .schoolId(student.getSchoolId())
                .name(student.getName())
                .gender(student.getGender())
                .genderName(getGenderName(student.getGender()))
                .birthDate(student.getBirthDate())
                .enrollmentDate(student.getEnrollmentDate())
                .status(student.getStatus())
                .statusName(getStatusName(student.getStatus()))
                .createdAt(student.getCreatedAt())
                .updatedAt(student.getUpdatedAt())
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
            case 0 -> "离校";
            case 1 -> "在读";
            case 2 -> "休学";
            default -> "未知";
        };
    }

}
