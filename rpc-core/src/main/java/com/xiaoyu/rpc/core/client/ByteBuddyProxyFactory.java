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
                            // 注意：这里直接返回 Future。
                            // 此时要求业务接口 Method 的返回类型必须是 CompletableFuture，否则会发生类型转换异常。
                            return rpcClient.sendRequest(request, method.getReturnType());
                        }
                    })).make().load(clazz.getClassLoader()).getLoaded().getConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("ByteBuddy代理创建失败", e);
        }
    }
}
