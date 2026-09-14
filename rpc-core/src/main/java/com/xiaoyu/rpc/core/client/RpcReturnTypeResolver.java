package com.xiaoyu.rpc.core.client;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.concurrent.CompletableFuture;

/**
 * 解析 RPC 代理方法真正需要反序列化的响应类型。
 */
final class RpcReturnTypeResolver {

    private RpcReturnTypeResolver() {
    }

    static boolean isAsync(Method method) {
        return CompletableFuture.class.isAssignableFrom(method.getReturnType());
    }

    static Class<?> resolvePayloadType(Method method) {
        if (!isAsync(method)) {
            return method.getReturnType();
        }

        Type genericReturnType = method.getGenericReturnType();
        if (!(genericReturnType instanceof ParameterizedType)) {
            throw new IllegalArgumentException(
                    "异步 RPC 方法必须声明具体泛型返回值 CompletableFuture<T>: " + method.toGenericString());
        }

        ParameterizedType parameterizedType = (ParameterizedType) genericReturnType;
        Type[] typeArguments = parameterizedType.getActualTypeArguments();
        if (typeArguments.length != 1 || !(typeArguments[0] instanceof Class<?>)) {
            throw new IllegalArgumentException(
                    "暂不支持参数化或未解析的异步 RPC 返回类型: " + genericReturnType.getTypeName());
        }

        return (Class<?>) typeArguments[0];
    }
}
