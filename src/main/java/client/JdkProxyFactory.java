package client;

import Serialization.Serializer;
import Serialization.SerializerCode;
import VO.RpcRequest;
import com.google.protobuf.ByteString;
import config.RpcConfig;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class JdkProxyFactory implements ProxyFactory {

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
                            // 获取配置的序列化器
                            Serializer serializer = SerializerCode
                                    .getSerializerByCode(RpcConfig.getInstance().getSerializerCode());
                            for (Object arg : args) {
                                byte[] bytes = serializer.serialize(arg);
                                builder.addParameters(ByteString.copyFrom(bytes));
                            }
                        }

                        RpcRequest request = builder.build();
                        return new RpcClient().sendRequest(request, method.getReturnType());
                    }
                });
    }
}
