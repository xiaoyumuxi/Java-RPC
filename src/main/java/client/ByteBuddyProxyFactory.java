package client;

import VO.RpcRequest;
import com.google.protobuf.ByteString;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.implementation.InvocationHandlerAdapter;
import net.bytebuddy.matcher.ElementMatchers;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

public class ByteBuddyProxyFactory implements ProxyFactory {

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getProxy(Class<T> clazz) {
        try {
            return (T) new ByteBuddy()
                    .subclass(clazz)
                    .method(ElementMatchers.any())
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
                                for (Object arg : args) {
                                    byte[] bytes = objectToBytes(arg);
                                    builder.addParameters(ByteString.copyFrom(bytes));
                                }
                            }

                            RpcRequest request = builder.build();
                            return new RpcClient().sendRequest(request);
                        }
                    }))
                    .make()
                    .load(clazz.getClassLoader())
                    .getLoaded()
                    .getConstructor()
                    .newInstance();
        } catch (Exception e) {
            throw new RuntimeException("ByteBuddy代理创建失败", e);
        }
    }

    private byte[] objectToBytes(Object obj) {
        try (java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(bos)) {
            oos.writeObject(obj);
            oos.flush();
            return bos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("参数序列化失败", e);
        }
    }
}
