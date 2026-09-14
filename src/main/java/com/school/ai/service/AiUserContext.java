package com.school.ai.service;

import com.school.security.model.LoginUser;
import reactor.core.scheduler.Schedulers;

/**
 * 请求级用户上下文
 *
 * 解决 Spring AI Function Calling 在 Reactor 线程执行工具时 ThreadLocal 丢失的问题。
 *
 * 原理：注册 Reactor onScheduleHook，每次 Runnable 被调度执行时，
 * 将调度时捕获的用户恢复到执行线程的 ThreadLocal，执行完毕后清除。
 * 这样无论线程是新建还是复用，都能拿到正确的用户，不存在污染。
 */
public final class AiUserContext {

    private static final ThreadLocal<LoginUser> holder = new ThreadLocal<>();
    private static final String HOOK_KEY = "ai-user-context";

    static {
        // ponytail: 全局注册一次，所有 Reactor 调度线程自动继承当前用户
        Schedulers.onScheduleHook(HOOK_KEY, runnable -> {
            LoginUser captured = holder.get();
            if (captured == null) return runnable;
            return () -> {
                LoginUser prev = holder.get();
                holder.set(captured);
                try {
                    runnable.run();
                } finally {
                    if (prev == null) holder.remove();
                    else holder.set(prev);
                }
            };
        });
    }

    private AiUserContext() {}

    public static void set(LoginUser user) {
        holder.set(user);
    }

    public static LoginUser get() {
        return holder.get();
    }

    public static void clear() {
        holder.remove();
    }

}
