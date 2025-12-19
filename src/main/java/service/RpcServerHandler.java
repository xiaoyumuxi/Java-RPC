package service;

// 务必导入生成的类
import VO.RpcRequest;
import VO.RpcResponse;

import com.google.protobuf.ByteString;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RpcServerHandler extends SimpleChannelInboundHandler<RpcRequest> {

    // 模拟注册中心
    private static final Map<String, Object> SERVICE_MAP = new ConcurrentHashMap<>();

    public static void registerService(String interfaceName, Object serviceBean) {
        SERVICE_MAP.put(interfaceName, serviceBean);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcRequest request) throws Exception {
        RpcResponse.Builder responseBuilder = RpcResponse.newBuilder();

        try {
            // 1. 获取实现类
            Object serviceBean = SERVICE_MAP.get(request.getInterfaceName());
            if (serviceBean == null) {
                throw new RuntimeException("未找到服务实现: " + request.getInterfaceName());
            }

            // 2. 解析参数类型 (List<String> -> Class<?>[])
            // Proto 存的是类名字符串，我们需要反射还原成 Class 对象
            List<String> paramTypeNames = request.getParamTypesList();
            Class<?>[] parameterTypes = new Class[paramTypeNames.size()];
            for (int i = 0; i < paramTypeNames.size(); i++) {
                // Class.forName 可能抛出 ClassNotFoundException
                parameterTypes[i] = Class.forName(paramTypeNames.get(i));
            }

            // 3. 解析参数值 (List<ByteString> -> Object[])
            // Proto 存的是二进制，我们需要反序列化回 Java 对象
            List<ByteString> paramByteList = request.getParametersList();
            Object[] parameters = new Object[paramByteList.size()];
            for (int i = 0; i < paramByteList.size(); i++) {
                byte[] bytes = paramByteList.get(i).toByteArray();
                parameters[i] = bytesToObject(bytes);
            }

            // 4. 反射调用
            Class<?> serviceClass = serviceBean.getClass();
            Method method = serviceClass.getMethod(request.getMethodName(), parameterTypes);
            Object result = method.invoke(serviceBean, parameters);

            // 5. 封装成功结果 (Object -> byte[] -> ByteString)
            byte[] resultBytes = objectToBytes(result);
            responseBuilder.setData(ByteString.copyFrom(resultBytes));
            responseBuilder.setMessage("Success");

        } catch (Exception e) {
            e.printStackTrace();
            responseBuilder.setMessage("Error: " + e.getMessage());
            // 可以在这里把异常对象也序列化传回去，或者只传错误信息
            responseBuilder.setData(ByteString.EMPTY);
        }

        // 6. 发送响应
        ctx.writeAndFlush(responseBuilder.build());
    }

    // --- 辅助方法：反序列化 (bytes -> Object) ---
    private Object bytesToObject(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        try (java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(bytes);
             java.io.ObjectInputStream ois = new java.io.ObjectInputStream(bis)) {
            return ois.readObject();
        } catch (Exception e) {
            throw new RuntimeException("服务端反序列化参数失败", e);
        }
    }

    // --- 辅助方法：序列化 (Object -> bytes) ---
    private byte[] objectToBytes(Object obj) {
        // 如果结果是 null，返回空数组
        if (obj == null) return new byte[0];
        try (java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
             java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(bos)) {
            oos.writeObject(obj);
            oos.flush();
            return bos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("服务端序列化结果失败", e);
        }
    }
}