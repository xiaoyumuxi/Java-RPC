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
                            // 这里不阻塞等待结果，直接把 CompletableFuture 返回给上层调用方。
                            // 如果业务接口不是异步返回类型，运行时会出现类型不匹配。
                            return rpcClient.sendRequest(request, method.getReturnType());
                        }
                    })).make().load(clazz.getClassLoader()).getLoaded().getConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("ByteBuddy代理创建失败", e);
        }
    }
}
