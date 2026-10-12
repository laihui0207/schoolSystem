package com.school.module.pdf.dto;

import com.school.module.class_module.entity.SchoolClass;
import com.school.module.exam.entity.Exam;
import com.school.module.score.entity.Score;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 一个班级在一场考试下生成成绩单所需的共享上下文：全班每人一页时只算一次
 *
 * @param exam          考试
 * @param schoolName    学校名
 * @param termName      学期展示名
 * @param schoolClass   班级
 * @param gradeName     年级名
 * @param subjects      考试科目（按 exam_subject.sort_order 排序）与满分
 * @param classScores   该班本场全部成绩行
 * @param classAverages 班级均分（总分 + 各科）
 * @param gradeAverages 年级均分（总分 + 各科）
 * @author School System
 */
public record ReportContext(Exam exam,
                            String schoolName,
                            String termName,
                            SchoolClass schoolClass,
                            String gradeName,
                            List<SubjectMeta> subjects,
                            List<Score> classScores,
                            GroupAverages classAverages,
                            GroupAverages gradeAverages) {

    public String className() {
        return schoolClass == null || schoolClass.getClassName() == null ? "" : schoolClass.getClassName();
    }

    /**
     * 科目元信息：名称 + 本场满分
     */
    public record SubjectMeta(Long subjectId, String subjectName, BigDecimal fullScore) {
    }

    /**
     * 一个群体（班级/年级）的均分：总分均分 + 科目ID -> 单科均分；无有效成绩时 totalAverage 为 null
     */
    public record GroupAverages(BigDecimal totalAverage, Map<Long, BigDecimal> subjectAverages) {

        public static GroupAverages empty() {
            return new GroupAverages(null, Map.of());
        }

        public BigDecimal subjectAverage(Long subjectId) {
            return subjectId == null || subjectAverages == null ? null : subjectAverages.get(subjectId);
        }
    }

}
