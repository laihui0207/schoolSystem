package com.school.module.score.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.module.score.dto.ScoreQueryRequest;
import com.school.module.score.entity.Score;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 成绩Mapper
 *
 * @author School System
 */
@Mapper
public interface ScoreMapper extends BaseMapper<Score> {

    /**
     * 分页查询成绩列表
     */
    Page<Score> selectPageByQuery(@Param("page") Page<Score> page,
                                  @Param("request") ScoreQueryRequest request);

    /**
     * 查询成绩是否已存在
     */
    Score selectByExamStudentSubject(@Param("examId") Long examId,
                                    @Param("studentId") Long studentId,
                                    @Param("subjectId") Long subjectId);

    /**
     * 批量查询某次考试的成绩
     */
    List<Score> selectByExamId(@Param("examId") Long examId,
                              @Param("classId") Long classId);

    /**
     * 审核列表（按考试+班级+科目聚合）
     */
    List<Map<String, Object>> selectReviewList(@Param("examId") Long examId,
                                                @Param("classId") Long classId,
                                                @Param("subjectId") Long subjectId,
                                                @Param("schoolId") Long schoolId);

    /**
     * 班级+科目的成绩列表（含学生信息）
     */
    List<Map<String, Object>> selectClassScoreItems(@Param("examId") Long examId,
                                                     @Param("classId") Long classId,
                                                     @Param("subjectId") Long subjectId);

    /**
     * 考试录入进度（按班级+科目统计）
     */
    List<Map<String, Object>> selectExamProgress(@Param("examId") Long examId);

}
