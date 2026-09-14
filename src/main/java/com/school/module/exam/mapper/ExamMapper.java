package com.school.module.exam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.module.exam.dto.ExamQueryRequest;
import com.school.module.exam.entity.Exam;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 考试Mapper
 *
 * @author School System
 */
@Mapper
public interface ExamMapper extends BaseMapper<Exam> {

    /**
     * 分页查询考试列表
     *
     * @param page 分页对象
     * @param request 查询条件
     * @return 分页结果
     */
    Page<Exam> selectPageByQuery(@Param("page") Page<Exam> page,
                                 @Param("request") ExamQueryRequest request);

}
