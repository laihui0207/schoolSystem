package com.school.module.subject.dto;

import com.school.common.base.BasePageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * 科目查询请求
 *
 * @author School System
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class SubjectQueryRequest extends BasePageQuery {

    private Long schoolId;
    private String subjectName;
    private String subjectCode;

    public SubjectQueryRequest() {
        super();
    }

}
