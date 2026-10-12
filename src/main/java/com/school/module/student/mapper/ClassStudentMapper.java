package com.school.module.student.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.school.module.student.dto.StudentClassRow;
import com.school.module.student.entity.ClassStudent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/**
 * 班级学生关联Mapper
 *
 * @author School System
 */
@Mapper
public interface ClassStudentMapper extends BaseMapper<ClassStudent> {

    /**
     * 查询学生所在的所有班级ID
     *
     * @param studentId 学生ID
     * @return 班级ID列表
     */
    List<Long> selectClassIdsByStudentId(@Param("studentId") Long studentId);

    /**
     * 批量查询多个学生的班级信息（含班级名、年级名），一次查完避免 N+1
     *
     * @param studentIds 学生ID集合，不可为空
     * @return 扁平的学生-班级行
     */
    List<StudentClassRow> selectClassRowsByStudentIds(@Param("studentIds") Collection<Long> studentIds);

    /**
     * 查询班级下的所有学生ID
     *
     * @param classId 班级ID
     * @return 学生ID列表
     */
    List<Long> selectStudentIdsByClassId(@Param("classId") Long classId);

    /**
     * 删除学生的所有班级关联
     *
     * @param studentId 学生ID
     * @return 删除数量
     */
    int deleteByStudentId(@Param("studentId") Long studentId);

    /**
     * 删除班级的所有学生关联（班级删除时清理）
     *
     * @param classId 班级ID
     * @return 删除数量
     */
    // ponytail: G-4 — class 只做逻辑删，class_student 残留行会让学生永远卡在「已在其他班」
    int deleteByClassId(@Param("classId") Long classId);

    /**
     * 删除学生在某个班级的关联
     *
     * @param studentId 学生ID
     * @param classId 班级ID
     * @return 删除数量
     */
    int deleteByStudentIdAndClassId(@Param("studentId") Long studentId,
                                   @Param("classId") Long classId);

}
