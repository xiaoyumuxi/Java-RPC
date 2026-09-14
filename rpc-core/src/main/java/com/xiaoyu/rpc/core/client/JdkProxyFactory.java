package com.xiaoyu.rpc.core.client;

import com.google.protobuf.ByteString;
import com.xiaoyu.rpc.common.serialization.Serializer;
import com.xiaoyu.rpc.common.serialization.SerializerCode;
import com.xiaoyu.rpc.common.vo.RpcRequest;
import com.xiaoyu.rpc.core.config.RpcConfig;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class JdkProxyFactory implements ProxyFactory {

    private volatile RpcClient rpcClient;
    private volatile boolean closed;

    public JdkProxyFactory() {
        // SPI 扩展加载阶段保持轻量，不在构造时初始化注册中心和传输层。
    }

    JdkProxyFactory(RpcClient rpcClient) {
        this.rpcClient = Objects.requireNonNull(rpcClient, "rpcClient");
    }

    private RpcClient getRpcClient() {
        if (closed) {
            throw new IllegalStateException("JdkProxyFactory 已关闭");
        }

        RpcClient client = rpcClient;
        if (client == null) {
            synchronized (this) {
                if (closed) {
                    throw new IllegalStateException("JdkProxyFactory 已关闭");
                }
                client = rpcClient;
                if (client == null) {
                    client = new RpcClient();
                    rpcClient = client;
                }
            }
        }
        return client;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getProxy(Class<T> clazz) {
        return (T) Proxy.newProxyInstance(
                clazz.getClassLoader(),
                new Class[] { clazz },
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        RpcRequest.Builder builder = RpcRequest.newBuilder()
                                .setInterfaceName(method.getDeclaringClass().getName())
                                .setMethodName(method.getName());

                        Class<?>[] parameterTypes = method.getParameterTypes();
                        for (Class<?> paramType : parameterTypes) {
                            builder.addParamTypes(paramType.getName());
                        }

                        if (args != null) {
                            Serializer serializer = SerializerCode
                                    .getSerializerByCode(RpcConfig.getInstance().getSerializerCode());
                            for (Object arg : args) {
                                byte[] bytes = serializer.serialize(arg);
                                builder.addParameters(ByteString.copyFrom(bytes));
                            }
                        }

                        RpcRequest request = builder.build();
                        boolean async = RpcReturnTypeResolver.isAsync(method);
                        Class<?> payloadType = RpcReturnTypeResolver.resolvePayloadType(method);
                        CompletableFuture<Object> future = getRpcClient().sendRequest(request, payloadType);

                        if (async) {
                            return future;
                        }
                        return future.get();
                    }
                });
    }

    @Override
    public void close() {
        RpcClient client;
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            client = rpcClient;
            rpcClient = null;
        }

        if (client != null) {
            client.close();
        }
    }
}
