package com.school.common.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 响应状态码枚举
 *
 * @author School System
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    // 成功
    SUCCESS(200, "操作成功"),

    // 失败
    FAIL(500, "操作失败"),

    // 参数错误
    PARAM_ERROR(400, "参数错误"),
    PARAM_MISSING(401, "缺少必要参数"),
    PARAM_INVALID(402, "参数格式不正确"),

    // 认证相关
    UNAUTHORIZED(1001, "未认证或认证已过期"),
    TOKEN_INVALID(1002, "Token无效"),
    TOKEN_EXPIRED(1003, "Token已过期"),
    LOGIN_FAIL(1004, "用户名或密码错误"),
    CAPTCHA_ERROR(1005, "验证码错误"),
    CAPTCHA_EXPIRED(1006, "验证码已过期"),

    // 权限相关
    FORBIDDEN(1101, "无权限访问"),
    ACCESS_DENIED(1102, "访问被拒绝"),

    // 用户相关
    USER_NOT_FOUND(2001, "用户不存在"),
    USER_DISABLED(2002, "用户已被禁用"),
    USER_EXISTS(2003, "用户已存在"),
    PASSWORD_ERROR(2004, "密码错误"),
    PHONE_EXISTS(2005, "手机号已被注册"),

    // 学校相关
    SCHOOL_NOT_FOUND(3001, "学校不存在"),
    SCHOOL_DISABLED(3002, "学校已被禁用"),

    // 班级相关
    CLASS_NOT_FOUND(4001, "班级不存在"),
    STUDENT_NOT_IN_CLASS(4002, "学生不在该班级"),

    // 成绩相关
    SCORE_NOT_FOUND(5001, "成绩不存在"),
    SCORE_ALREADY_PUBLISHED(5002, "成绩已发布"),
    SCORE_NOT_REVIEWED(5003, "成绩未审核"),
    SCORE_INVALID(5004, "成绩数据无效"),
    SCORE_OUT_OF_RANGE(5005, "成绩超出范围"),

    // 考试相关
    EXAM_NOT_FOUND(6001, "考试不存在"),
    EXAM_ALREADY_STARTED(6002, "考试已开始"),
    EXAM_NOT_STARTED(6003, "考试未开始"),
    EXAM_ENDED(6004, "考试已结束"),

    // 数据相关
    DATA_NOT_FOUND(7001, "数据不存在"),
    DATA_ALREADY_EXISTS(7002, "数据已存在"),
    DATA_IN_USE(7003, "数据正在使用中，无法删除"),

    // 文件相关
    FILE_UPLOAD_ERROR(8001, "文件上传失败"),
    FILE_TYPE_ERROR(8002, "文件类型不支持"),
    FILE_SIZE_ERROR(8003, "文件大小超出限制"),
    FILE_NOT_FOUND(8004, "文件不存在"),

    // 系统错误
    SYSTEM_ERROR(9001, "系统错误"),
    SERVICE_UNAVAILABLE(9002, "服务不可用"),
    DATABASE_ERROR(9003, "数据库错误"),
    REDIS_ERROR(9004, "缓存错误"),
    NETWORK_ERROR(9005, "网络错误");

    /**
     * 状态码
     */
    private final Integer code;

    /**
     * 消息
     */
    private final String message;

}
