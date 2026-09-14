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

    private final RpcClient rpcClient;

    public JdkProxyFactory() {
        this(new RpcClient());
    }

    JdkProxyFactory(RpcClient rpcClient) {
        this.rpcClient = Objects.requireNonNull(rpcClient, "rpcClient");
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
                        if (parameterTypes != null) {
                            for (Class<?> paramType : parameterTypes) {
                                builder.addParamTypes(paramType.getName());
                            }
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
                        CompletableFuture<Object> future = rpcClient.sendRequest(request, method.getReturnType());
                        // 如果业务接口声明的返回类型是异步的，直接返回 Future；否则阻塞等待结果
                        if (CompletableFuture.class.isAssignableFrom(method.getReturnType())) {
                            return future;
                        }
                        return future.get();
                    }
                });
    }
}
