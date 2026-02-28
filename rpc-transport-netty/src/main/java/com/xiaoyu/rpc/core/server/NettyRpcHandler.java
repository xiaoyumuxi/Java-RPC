package com.xiaoyu.rpc.core.server;

// 务必导入生成的类
import com.xiaoyu.rpc.common.vo.RpcRequest;
import com.xiaoyu.rpc.common.vo.RpcResponse;
import com.xiaoyu.rpc.common.serialization.Serializer;
import com.xiaoyu.rpc.common.serialization.SerializerCode;
import com.xiaoyu.rpc.core.config.RpcConfig;

import com.google.protobuf.ByteString;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ChannelHandler.Sharable
public class NettyRpcHandler extends SimpleChannelInboundHandler<RpcRequest> {

    private static final Logger log = LoggerFactory.getLogger(NettyRpcHandler.class);

    // 移除内部 Map，改用 ServiceRepository

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcRequest request) throws Exception {
        RpcResponse.Builder responseBuilder = RpcResponse.newBuilder();
        responseBuilder.setRequestId(request.getRequestId());

        try {
            // 从 ServiceRepository 取到目标服务实现
            Object serviceBean = ServiceRepository.getService(request.getInterfaceName());
            if (serviceBean == null) {
                throw new RuntimeException("未找到服务实现: " + request.getInterfaceName());
            }

            // 将参数类型名还原为 Class<?>[]
            // Proto 存的是类名字符串，我们需要反射还原成 Class 对象
            List<String> paramTypeNames = request.getParamTypesList();
            Class<?>[] parameterTypes = new Class[paramTypeNames.size()];
            for (int i = 0; i < paramTypeNames.size(); i++) {
                // Class.forName 可能抛出 ClassNotFoundException
                parameterTypes[i] = Class.forName(paramTypeNames.get(i));
            }

            // 将参数字节反序列化为方法入参
            // Proto 存的是二进制，我们需要反序列化回 Java 对象
            List<ByteString> paramByteList = request.getParametersList();
            Object[] parameters = new Object[paramByteList.size()];

            // 获取序列化器
            Serializer serializer = SerializerCode.getSerializerByCode(RpcConfig.getInstance().getSerializerCode());

            for (int i = 0; i < paramByteList.size(); i++) {
                byte[] bytes = paramByteList.get(i).toByteArray();
                parameters[i] = serializer.deserialize(bytes, parameterTypes[i]);
            }

            // 通过反射调用目标方法
            Class<?> serviceClass = serviceBean.getClass();
            Method method = serviceClass.getMethod(request.getMethodName(), parameterTypes);
            Object result = method.invoke(serviceBean, parameters);

            // 把返回值序列化后写入响应
            byte[] resultBytes;
            if (result == null) {
                resultBytes = new byte[0];
            } else {
                resultBytes = serializer.serialize(result);
            }

            responseBuilder.setData(ByteString.copyFrom(resultBytes));
            responseBuilder.setMessage("Success");

        } catch (Exception e) {
            log.error("Failed to process RPC request: interface={}, method={}, requestId={}",
                    request.getInterfaceName(), request.getMethodName(), request.getRequestId(), e);
            responseBuilder.setMessage("Error: " + e.getMessage());
            // 可以在这里把异常对象也序列化传回去，或者只传错误信息
            responseBuilder.setData(ByteString.EMPTY);
        }

        // 返回响应
        ctx.writeAndFlush(responseBuilder.build());
    }
}
