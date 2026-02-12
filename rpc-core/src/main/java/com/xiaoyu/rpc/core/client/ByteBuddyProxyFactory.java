package com.xiaoyu.rpc.core.client;

import com.xiaoyu.rpc.common.serialization.Serializer;
import com.xiaoyu.rpc.common.serialization.SerializerCode;
import com.xiaoyu.rpc.common.vo.RpcRequest;
import com.google.protobuf.ByteString;
import com.xiaoyu.rpc.core.config.RpcConfig;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.implementation.InvocationHandlerAdapter;
import net.bytebuddy.matcher.ElementMatchers;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

public class ByteBuddyProxyFactory implements ProxyFactory {

    private final RpcClient rpcClient;

    public ByteBuddyProxyFactory() {
        this.rpcClient = new RpcClient();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getProxy(Class<T> clazz) {
        try {
            return (T) new ByteBuddy().subclass(clazz).method(ElementMatchers.any())
                    .intercept(InvocationHandlerAdapter.of(new InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                            // 请求中记录接口名 + 方法名，服务端靠这两项定位目标方法
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
                                // 获取配置的序列化器
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
                    })).make().load(clazz.getClassLoader()).getLoaded().getConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("ByteBuddy代理创建失败", e);
        }
    }
}
