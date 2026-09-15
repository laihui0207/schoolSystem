package com.school.module.class_module.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.common.exception.BusinessException;
import com.school.module.class_module.dto.ClassCreateRequest;
import com.school.module.class_module.dto.ClassQueryRequest;
import com.school.module.class_module.dto.ClassResponse;
import com.school.module.class_module.dto.ClassUpdateRequest;
import com.school.module.class_module.entity.SchoolClass;
import com.school.module.class_module.mapper.ClassMapper;
import com.school.module.grade.entity.Grade;
import com.school.module.grade.mapper.GradeMapper;
import com.school.module.school.entity.School;
import com.school.module.school.mapper.SchoolMapper;
import com.school.module.teacher.entity.Teacher;
import com.school.module.teacher.mapper.TeacherMapper;
import com.school.module.term.entity.AcademicTerm;
import com.school.module.term.mapper.AcademicTermMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 班级服务实现类
 *
 * @author School System
 */
@Service
@RequiredArgsConstructor
public class ClassServiceImpl implements ClassService {

    private final ClassMapper classMapper;
    private final SchoolMapper schoolMapper;
    private final GradeMapper gradeMapper;
    private final AcademicTermMapper academicTermMapper;
    private final TeacherMapper teacherMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ClassResponse createClass(ClassCreateRequest request) {
        // 校验班级编码是否已存在
        SchoolClass existingClass = classMapper.selectBySchoolGradeCode(
                request.getSchoolId(),
                request.getGradeId(),
                request.getClassCode()
        );
        if (existingClass != null) {
            throw new BusinessException("该年级下的班级编码已存在");
        }

        // 构建班级实体
        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setSchoolId(request.getSchoolId());
        schoolClass.setGradeId(request.getGradeId());
        schoolClass.setClassName(request.getClassName());
        schoolClass.setClassCode(request.getClassCode());
        schoolClass.setTermId(request.getTermId());
        schoolClass.setHeadTeacherId(request.getHeadTeacherId());

        // 保存到数据库
        classMapper.insert(schoolClass);

        // 返回响应
        return convertToResponse(schoolClass);
    }

    @Override
    public ClassResponse getClassById(Long id) {
        SchoolClass schoolClass = classMapper.selectById(id);
        if (schoolClass == null) {
            throw new BusinessException("班级不存在");
        }
        return convertToResponse(schoolClass);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ClassResponse updateClass(Long id, ClassUpdateRequest request) {
        // 查询班级是否存在
        SchoolClass existingClass = classMapper.selectById(id);
        if (existingClass == null) {
            throw new BusinessException("班级不存在");
        }

        // 构建更新后的班级对象
        SchoolClass updatedClass = new SchoolClass();
        updatedClass.setId(existingClass.getId());
        updatedClass.setSchoolId(existingClass.getSchoolId());
        updatedClass.setGradeId(existingClass.getGradeId());
        updatedClass.setClassName(request.getClassName() != null ? request.getClassName() : existingClass.getClassName());
        updatedClass.setClassCode(request.getClassCode() != null ? request.getClassCode() : existingClass.getClassCode());
        updatedClass.setTermId(existingClass.getTermId());
        updatedClass.setHeadTeacherId(request.getHeadTeacherId() != null ? request.getHeadTeacherId() : existingClass.getHeadTeacherId());

        // 更新数据库
        classMapper.updateById(updatedClass);

        // 返回更新后的数据
        return convertToResponse(updatedClass);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setHeadTeacher(Long id, Long headTeacherId) {
        // 查询班级是否存在
        SchoolClass existingClass = classMapper.selectById(id);
        if (existingClass == null) {
            throw new BusinessException("班级不存在");
        }

        // 构建更新对象
        SchoolClass updatedClass = new SchoolClass();
        updatedClass.setId(existingClass.getId());
        updatedClass.setSchoolId(existingClass.getSchoolId());
        updatedClass.setGradeId(existingClass.getGradeId());
        updatedClass.setClassName(existingClass.getClassName());
        updatedClass.setClassCode(existingClass.getClassCode());
        updatedClass.setTermId(existingClass.getTermId());
        updatedClass.setHeadTeacherId(headTeacherId);

        // 更新数据库
        classMapper.updateById(updatedClass);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteClass(Long id) {
        // 查询班级是否存在
        SchoolClass existingClass = classMapper.selectById(id);
        if (existingClass == null) {
            throw new BusinessException("班级不存在");
        }

        // 逻辑删除
        classMapper.deleteById(id);
    }

    @Override
    public Page<ClassResponse> listClasses(ClassQueryRequest request) {
        // 构建分页对象
        Page<SchoolClass> page = new Page<>(request.getPageNum(), request.getPageSize());

        // 分页查询
        Page<SchoolClass> resultPage = classMapper.selectPageByQuery(page, request);

        // 转换为响应DTO
        Page<ClassResponse> responsePage = new Page<>(
                resultPage.getCurrent(),
                resultPage.getSize(),
                resultPage.getTotal()
        );

        List<ClassResponse> responses = resultPage.getRecords().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());

        responsePage.setRecords(responses);

        return responsePage;
    }

    /**
     * 转换为响应DTO
     */
    private ClassResponse convertToResponse(SchoolClass schoolClass) {
        School school = schoolClass.getSchoolId() != null ? schoolMapper.selectById(schoolClass.getSchoolId()) : null;
        Grade grade = schoolClass.getGradeId() != null ? gradeMapper.selectById(schoolClass.getGradeId()) : null;
        AcademicTerm term = schoolClass.getTermId() != null ? academicTermMapper.selectById(schoolClass.getTermId()) : null;
        Teacher teacher = schoolClass.getHeadTeacherId() != null ? teacherMapper.selectById(schoolClass.getHeadTeacherId()) : null;

        String termName = term != null
                ? term.getYear() + " " + (term.getTerm() == 1 ? "上学期" : "下学期")
                : null;

        return ClassResponse.builder()
                .id(schoolClass.getId())
                .schoolId(schoolClass.getSchoolId())
                .schoolName(school != null ? school.getSchoolName() : null)
                .gradeId(schoolClass.getGradeId())
                .gradeName(grade != null ? grade.getGradeName() : null)
                .className(schoolClass.getClassName())
                .classCode(schoolClass.getClassCode())
                .termId(schoolClass.getTermId())
                .termName(termName)
                .headTeacherId(schoolClass.getHeadTeacherId())
                .headTeacherName(teacher != null ? teacher.getName() : null)
                .createdAt(schoolClass.getCreatedAt())
                .updatedAt(schoolClass.getUpdatedAt())
                .build();
    }

}
