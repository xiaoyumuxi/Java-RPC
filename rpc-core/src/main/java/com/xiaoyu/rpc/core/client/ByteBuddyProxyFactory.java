package com.xiaoyu.rpc.core.client;

import com.google.protobuf.ByteString;
import com.xiaoyu.rpc.common.serialization.Serializer;
import com.xiaoyu.rpc.common.serialization.SerializerCode;
import com.xiaoyu.rpc.common.vo.RpcRequest;
import com.xiaoyu.rpc.core.config.RpcConfig;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.implementation.InvocationHandlerAdapter;
import net.bytebuddy.matcher.ElementMatchers;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class ByteBuddyProxyFactory implements ProxyFactory {

    private volatile RpcClient rpcClient;
    private volatile boolean closed;

    public ByteBuddyProxyFactory() {
        // 与 JDK Proxy 一致：SPI 扩展加载阶段不初始化注册中心和传输层。
    }

    ByteBuddyProxyFactory(RpcClient rpcClient) {
        this.rpcClient = Objects.requireNonNull(rpcClient, "rpcClient");
    }

    private RpcClient getRpcClient() {
        if (closed) {
            throw new IllegalStateException("ByteBuddyProxyFactory 已关闭");
        }

        RpcClient client = rpcClient;
        if (client == null) {
            synchronized (this) {
                if (closed) {
                    throw new IllegalStateException("ByteBuddyProxyFactory 已关闭");
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
        try {
            return (T) new ByteBuddy().subclass(clazz).method(ElementMatchers.any())
                    .intercept(InvocationHandlerAdapter.of(new InvocationHandler() {
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
                    })).make().load(clazz.getClassLoader()).getLoaded().getConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("ByteBuddy代理创建失败", e);
        }
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
