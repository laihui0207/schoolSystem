package com.school.module.backup.dto;

import com.school.common.base.BasePageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * 备份记录分页查询
 * <p>
 * ponytail: 父类 BasePageQuery 是 @SuperBuilder，子类必须同为 @SuperBuilder；显式无参构造给 Spring 绑定 GET 参数用。
 *
 * @author School System
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class BackupQueryRequest extends BasePageQuery {

    /**
     * 0-进行中，1-成功，2-失败
     */
    private Integer status;

    /**
     * 1-手动，2-定时
     */
    private Integer backupType;

    public BackupQueryRequest() {
        super();
    }

}
