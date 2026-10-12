package com.school.module.log.aspect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.school.module.log.entity.OperationLog;
import com.school.module.log.service.OperationLogService;
import com.school.security.model.LoginUser;
import com.school.security.util.SecurityUtils;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * 操作日志切面：自动记录所有 Controller 的写请求（POST/PUT/PATCH/DELETE）
 * <p>
 * ponytail: 不要求逐个方法加 @OperationLog——靠 HTTP 方法注解兜底全覆盖，注解只用于给出更友好的中文描述。
 * ponytail: @Order(50) 让本切面排在方法安全拦截器（PreAuthorize 的 order 是 200）外层，
 * 这样被 @PreAuthorize 拒绝的越权尝试也会留下一条 status=0「无权限访问」的记录，而不是静默消失。
 * ponytail: 切面内任何异常只 warn 不上抛；日志写不进去不能让业务请求失败。
 *
 * @author School System
 */
@Aspect
@Component
@Order(50)
@Slf4j
public class OperationLogAspect {

    static final String MASK = "***";
    static final int PARAMS_MAX_LENGTH = 2000;
    static final int ERROR_MAX_LENGTH = 500;
    private static final int OPERATION_MAX_LENGTH = 100;
    private static final int METHOD_MAX_LENGTH = 200;
    private static final int IP_MAX_LENGTH = 50;
    private static final int USERNAME_MAX_LENGTH = 50;
    private static final String USERNAME_FIELD = "username";
    // ponytail: authorization 也要打码——/auth/logout 以 @RequestHeader 形参接收 Bearer 原文，否则 JWT 会原样进库
    private static final List<String> SENSITIVE_KEYS = List.of("password", "token", "secret", "authorization");

    private final OperationLogService operationLogService;
    private final ObjectMapper objectMapper;

    public OperationLogAspect(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
        // ponytail: 自建 ObjectMapper 而不注入容器里的——RedisConfig 的实例可能带 defaultTyping 把 @class 混进 JSON，
        // 审计参数只要求可读；日期按字符串输出，空 bean 不报错
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    }

    @Pointcut("within(com.school..controller..*)")
    public void inController() {
    }

    @Pointcut("@annotation(org.springframework.web.bind.annotation.PostMapping)"
            + " || @annotation(org.springframework.web.bind.annotation.PutMapping)"
            + " || @annotation(org.springframework.web.bind.annotation.PatchMapping)"
            + " || @annotation(org.springframework.web.bind.annotation.DeleteMapping)")
    public void writeMapping() {
    }

    @Around("inController() && writeMapping()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        Throwable failure = null;
        try {
            return joinPoint.proceed();
        } catch (Throwable t) {
            failure = t;
            throw t;
        } finally {
            safeRecord(joinPoint, start, failure);
        }
    }

    private void safeRecord(ProceedingJoinPoint joinPoint, long start, Throwable failure) {
        try {
            operationLogService.record(buildLog(joinPoint, System.currentTimeMillis() - start, failure));
        } catch (Exception e) {
            log.warn("操作日志记录失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 组装日志实体；对测试与直接调用公开为包级可见
     */
    OperationLog buildLog(ProceedingJoinPoint joinPoint, long executeTime, Throwable failure) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        HttpServletRequest request = currentRequest();

        ObjectNode paramsNode = collectParams(signature.getParameterNames(), joinPoint.getArgs());
        String params = truncate(toJson(paramsNode), PARAMS_MAX_LENGTH);

        OperationLog.OperationLogBuilder builder = OperationLog.builder()
                .operation(truncate(resolveOperation(method), OPERATION_MAX_LENGTH))
                .method(truncate(resolveMethod(request, method), METHOD_MAX_LENGTH))
                .params(params)
                .ip(truncate(resolveIp(request), IP_MAX_LENGTH))
                .status(failure == null ? OperationLog.STATUS_SUCCESS : OperationLog.STATUS_FAIL)
                .errorMsg(failure == null ? null : truncate(resolveErrorMessage(failure), ERROR_MAX_LENGTH))
                .executeTime(executeTime);

        fillIdentity(builder, paramsNode);
        return builder.build();
    }

    /**
     * 已登录取 LoginUser；未登录（登录接口本身）从参数里捞 username，便于审计登录失败的账号
     */
    private void fillIdentity(OperationLog.OperationLogBuilder builder, ObjectNode paramsNode) {
        if (SecurityUtils.isAuthenticated()) {
            LoginUser user = SecurityUtils.currentUser();
            builder.userId(user.getUserId()).username(truncate(user.getUsername(), USERNAME_MAX_LENGTH));
            return;
        }
        JsonNode usernameNode = paramsNode.findValue(USERNAME_FIELD);
        if (usernameNode != null && usernameNode.isTextual()) {
            builder.username(truncate(usernameNode.asText(), USERNAME_MAX_LENGTH));
        }
    }

    private String resolveOperation(Method method) {
        com.school.module.log.annotation.OperationLog annotation =
                method.getAnnotation(com.school.module.log.annotation.OperationLog.class);
        String fallback = method.getDeclaringClass().getSimpleName() + "#" + method.getName();
        if (annotation == null || annotation.value().isBlank()) {
            return fallback;
        }
        return annotation.module().isBlank()
                ? annotation.value()
                : "[" + annotation.module() + "] " + annotation.value();
    }

    private String resolveMethod(HttpServletRequest request, Method method) {
        if (request == null) {
            return method.getDeclaringClass().getSimpleName() + "#" + method.getName();
        }
        return request.getMethod() + " " + request.getRequestURI();
    }

    private String resolveIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // ponytail: 经过多级代理时 XFF 是逗号分隔链，第一个才是客户端
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private String resolveErrorMessage(Throwable failure) {
        String message = failure.getMessage();
        if (failure instanceof AccessDeniedException) {
            return "无权限访问" + (message == null ? "" : "（" + message + "）");
        }
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    private HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        return attributes instanceof ServletRequestAttributes servletAttributes
                ? servletAttributes.getRequest()
                : null;
    }

    /**
     * 按形参名收集可序列化参数，跳过 Servlet/文件流等对象，再对敏感字段打码
     */
    private ObjectNode collectParams(String[] parameterNames, Object[] args) {
        ObjectNode root = objectMapper.createObjectNode();
        if (args == null) {
            return root;
        }
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            if (arg == null || isExcluded(arg)) {
                continue;
            }
            String name = parameterNames != null && i < parameterNames.length && parameterNames[i] != null
                    ? parameterNames[i]
                    : "arg" + i;
            try {
                root.set(name, objectMapper.valueToTree(arg));
            } catch (Exception e) {
                root.put(name, String.valueOf(arg));
            }
        }
        mask(root);
        return root;
    }

    private boolean isExcluded(Object arg) {
        if (arg instanceof ServletRequest || arg instanceof ServletResponse || arg instanceof HttpSession
                || arg instanceof MultipartFile || arg instanceof Errors || arg instanceof Principal
                || arg instanceof Authentication || arg instanceof InputStream || arg instanceof OutputStream
                || arg instanceof Reader || arg instanceof Writer) {
            return true;
        }
        if (arg instanceof MultipartFile[]) {
            return true;
        }
        if (arg instanceof Collection<?> collection && !collection.isEmpty()) {
            return collection.iterator().next() instanceof MultipartFile;
        }
        return false;
    }

    /**
     * 原地递归打码：键名含 password/token/secret/authorization（忽略大小写）的值替换为 ***
     */
    private void mask(JsonNode node) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            // ponytail: 先拷一份键名再改，避免边遍历边 replace 触发并发修改
            List<String> fieldNames = new ArrayList<>();
            objectNode.fieldNames().forEachRemaining(fieldNames::add);
            for (String fieldName : fieldNames) {
                if (isSensitive(fieldName)) {
                    objectNode.replace(fieldName, TextNode.valueOf(MASK));
                } else {
                    mask(objectNode.get(fieldName));
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : (ArrayNode) node) {
                mask(child);
            }
        }
    }

    static boolean isSensitive(String key) {
        if (key == null) {
            return false;
        }
        String lower = key.toLowerCase(Locale.ROOT);
        return SENSITIVE_KEYS.stream().anyMatch(lower::contains);
    }

    private String toJson(ObjectNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            return "{}";
        }
    }

    static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

}
