package com.school.module.log.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志描述注解
 * <p>
 * 切面 {@code OperationLogAspect} 会拦截所有 Controller 的 POST/PUT/PATCH/DELETE 方法，
 * 不加本注解也会记录（操作名回退为 {@code Controller#method}）；加了注解则用这里的中文描述，
 * 便于审计页面按「审核成绩」「发布考试」等业务词检索。
 *
 * @author School System
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLog {

    /**
     * 操作描述，如「审核成绩」
     */
    String value() default "";

    /**
     * 所属模块，如 score / exam / user；非空时以 [module] 前缀写入 operation 列
     */
    String module() default "";

}
