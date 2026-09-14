package com.school.module.exam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.school.module.exam.entity.ExamSubject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 考试科目关联Mapper
 *
 * @author School System
 */
@Mapper
public interface ExamSubjectMapper extends BaseMapper<ExamSubject> {

    /**
     * 根据考试ID查询科目列表
     *
     * @param examId 考试ID
     * @return 科目列表
     */
    List<ExamSubject> selectByExamId(@Param("examId") Long examId);

    /**
     * 删除考试的所有科目关联
     *
     * @param examId 考试ID
     * @return 删除数量
     */
    int deleteByExamId(@Param("examId") Long examId);

}
