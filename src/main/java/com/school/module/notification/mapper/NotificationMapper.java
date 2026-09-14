package com.school.module.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.module.notification.dto.NotificationQueryRequest;
import com.school.module.notification.entity.Notification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 通知Mapper
 *
 * @author School System
 */
@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {

    /**
     * 分页查询通知列表
     *
     * @param page 分页对象
     * @param request 查询条件
     * @return 分页结果
     */
    Page<Notification> selectPageByQuery(@Param("page") Page<Notification> page,
                                        @Param("request") NotificationQueryRequest request);

    /**
     * 获取未读通知数量
     *
     * @param userId 用户ID
     * @return 未读数量
     */
    Integer countUnread(@Param("userId") Long userId);

}
