package com.school.common.constant;

/**
 * 通用常量
 *
 * @author School System
 */
public interface CommonConstants {

    /**
     * 成功标记
     */
    Integer SUCCESS = 200;

    /**
     * 失败标记
     */
    Integer FAIL = 500;

    /**
     * 启用状态
     */
    Integer STATUS_ENABLED = 1;

    /**
     * 禁用状态
     */
    Integer STATUS_DISABLED = 0;

    /**
     * 删除标记
     */
    Integer DELETED = 1;

    /**
     * 未删除标记
     */
    Integer NOT_DELETED = 0;

    /**
     * 默认页码
     */
    Long DEFAULT_PAGE_NUM = 1L;

    /**
     * 默认每页大小
     */
    Long DEFAULT_PAGE_SIZE = 10L;

    /**
     * UTF-8编码
     */
    String UTF8 = "UTF-8";

    /**
     * 验证码Redis Key前缀
     */
    String CAPTCHA_CODE_KEY = "captcha:code:";

    /**
     * Token Redis Key前缀
     */
    String TOKEN_KEY = "token:";

    /**
     * 用户信息Redis Key前缀
     */
    String USER_INFO_KEY = "user:info:";

}
