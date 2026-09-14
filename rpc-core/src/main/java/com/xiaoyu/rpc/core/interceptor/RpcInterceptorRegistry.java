package com.xiaoyu.rpc.core.interceptor;

import com.xiaoyu.rpc.common.vo.RpcRequest;
import com.xiaoyu.rpc.common.vo.RpcResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

public final class RpcInterceptorRegistry {

    private static final Logger log = LoggerFactory.getLogger(RpcInterceptorRegistry.class);
    private static final CopyOnWriteArrayList<RpcInterceptor> INTERCEPTORS = new CopyOnWriteArrayList<>();

    private RpcInterceptorRegistry() {
    }

    public static void register(RpcInterceptor interceptor) {
        Objects.requireNonNull(interceptor, "interceptor");
        if (INTERCEPTORS.addIfAbsent(interceptor)) {
            INTERCEPTORS.sort(Comparator.comparingInt(RpcInterceptor::order));
        }
    }

    public static void unregister(RpcInterceptor interceptor) {
        INTERCEPTORS.remove(interceptor);
    }

    public static List<RpcInterceptor> getInterceptors() {
        return List.copyOf(INTERCEPTORS);
    }

    public static void clear() {
        INTERCEPTORS.clear();
    }

    public static RpcRequest before(RpcInvocationContext context, RpcRequest request) {
        RpcRequest current = request;
        for (RpcInterceptor interceptor : INTERCEPTORS) {
            current = Objects.requireNonNull(
                    interceptor.before(context, current),
                    () -> "RpcInterceptor.before must not return null: " + interceptor.getClass().getName());
        }
        return current;
    }

    public static void after(RpcInvocationContext context, RpcResponse response) {
        for (int i = INTERCEPTORS.size() - 1; i >= 0; i--) {
            try {
                INTERCEPTORS.get(i).after(context, response);
            } catch (Exception e) {
                log.warn("RPC interceptor after callback failed: {}", INTERCEPTORS.get(i).getClass().getName(), e);
            }
        }
    }

    public static void onError(RpcInvocationContext context, Throwable error) {
        for (int i = INTERCEPTORS.size() - 1; i >= 0; i--) {
            try {
                INTERCEPTORS.get(i).onError(context, error);
            } catch (Exception e) {
                log.warn("RPC interceptor error callback failed: {}", INTERCEPTORS.get(i).getClass().getName(), e);
            }
        }
    }
}
