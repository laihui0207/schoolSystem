package com.school.common.exception;

import com.school.common.result.Result;
import com.school.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.servlet.http.HttpServletRequest;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 *
 * @author School System
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常
     */
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<?> handleBusinessException(BusinessException e, HttpServletRequest request) {
        log.warn("业务异常: URI={}, Message={}", request.getRequestURI(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /**
     * 处理参数校验异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleValidationException(MethodArgumentNotValidException e, HttpServletRequest request) {
        String errors = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("参数校验异常: URI={}, Errors={}", request.getRequestURI(), errors);
        return Result.fail(ResultCode.PARAM_ERROR.getCode(), errors);
    }

    /**
     * 处理绑定异常
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleBindException(BindException e, HttpServletRequest request) {
        String errors = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("绑定异常: URI={}, Errors={}", request.getRequestURI(), errors);
        return Result.fail(ResultCode.PARAM_ERROR.getCode(), errors);
    }

    /**
     * 处理访问拒绝异常
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<?> handleAccessDeniedException(AccessDeniedException e, HttpServletRequest request) {
        log.warn("访问拒绝: URI={}, Message={}", request.getRequestURI(), e.getMessage());
        return Result.fail(ResultCode.FORBIDDEN);
    }

    /**
     * 处理唯一约束冲突（业务层未拦截到的重复数据，如已逻辑删除记录仍占用唯一列）
     */
    @ExceptionHandler(DuplicateKeyException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<?> handleDuplicateKeyException(DuplicateKeyException e, HttpServletRequest request) {
        log.warn("唯一约束冲突: URI={}, Message={}", request.getRequestURI(), e.getMostSpecificCause().getMessage());
        return Result.fail(ResultCode.DATA_ALREADY_EXISTS);
    }

    /**
     * 处理非法参数异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleIllegalArgumentException(IllegalArgumentException e, HttpServletRequest request) {
        log.warn("非法参数: URI={}, Message={}", request.getRequestURI(), e.getMessage());
        return Result.fail(ResultCode.PARAM_INVALID.getCode(), e.getMessage());
    }

    /**
     * 处理缺少请求参数
     *
     * 否则会掉进 handleException 变成 500「系统错误」，把「调用方少传参」
     * 伪装成「服务端崩了」，排查时白跑一趟去翻服务端日志
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleMissingParam(MissingServletRequestParameterException e, HttpServletRequest request) {
        String msg = "缺少必要参数: " + e.getParameterName();
        log.warn("缺少参数: URI={}, {}", request.getRequestURI(), msg);
        return Result.fail(ResultCode.PARAM_MISSING.getCode(), msg);
    }

    /**
     * 处理请求体无法解析（JSON 语法错误、编码错误、类型不符）
     *
     * 同样属于客户端错误，以前掉进 handleException 变成 500「系统错误」。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleNotReadable(HttpMessageNotReadableException e, HttpServletRequest request) {
        log.warn("请求体解析失败: URI={}, Message={}", request.getRequestURI(), e.getMostSpecificCause().getMessage());
        return Result.fail(ResultCode.PARAM_ERROR.getCode(), "请求体格式不正确，无法解析");
    }

    /**
     * 处理路径变量类型转换失败（如 /scores/class-list 被 /scores/{id} 路由吃掉）
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<?> handleTypeMismatch(MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        log.warn("路径参数类型不匹配: URI={}, param={}, value={}", request.getRequestURI(), e.getName(), e.getValue());
        return Result.fail(ResultCode.DATA_NOT_FOUND.getCode(),
                "参数 " + e.getName() + " 的值 '" + e.getValue() + "' 格式不正确（该路径可能不存在）");
    }

    /**
     * 处理其他异常
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<?> handleException(Exception e, HttpServletRequest request) {
        log.error("系统异常: URI={}", request.getRequestURI(), e);
        return Result.fail(ResultCode.SYSTEM_ERROR);
    }

}
