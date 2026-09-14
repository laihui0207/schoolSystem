package com.school.module.student.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.school.module.student.entity.ClassStudent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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
     * 删除学生在某个班级的关联
     *
     * @param studentId 学生ID
     * @param classId 班级ID
     * @return 删除数量
     */
    int deleteByStudentIdAndClassId(@Param("studentId") Long studentId,
                                   @Param("classId") Long classId);

}
