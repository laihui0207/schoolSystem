package com.school.module.notification.dto;

import com.school.common.base.BasePageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * 通知查询请求
 *
 * @author School System
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class NotificationQueryRequest extends BasePageQuery {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 类型
     */
    private Integer type;

    /**
     * 是否已读
     */
    private Integer isRead;

    public NotificationQueryRequest() {
        super();
    }

}
