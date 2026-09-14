package com.school.module.term.dto;

import com.school.common.base.BasePageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * 学期查询请求
 *
 * @author School System
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class AcademicTermQueryRequest extends BasePageQuery {

    private Long schoolId;
    private String year;
    /** 学期：1-上学期，2-下学期 */
    private Integer term;
    /** 状态：0-已结束，1-进行中 */
    private Integer status;

    public AcademicTermQueryRequest() {
        super();
    }

}
