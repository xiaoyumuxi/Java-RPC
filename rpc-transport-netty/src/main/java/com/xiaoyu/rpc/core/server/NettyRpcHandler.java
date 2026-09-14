package com.xiaoyu.rpc.core.server;

import com.google.protobuf.ByteString;
import com.xiaoyu.rpc.common.serialization.Serializer;
import com.xiaoyu.rpc.common.serialization.SerializerCode;
import com.xiaoyu.rpc.common.vo.RpcRequest;
import com.xiaoyu.rpc.common.vo.RpcResponse;
import com.xiaoyu.rpc.core.config.RpcConfig;
import com.xiaoyu.rpc.core.util.TypeUtils;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RejectedExecutionException;

@ChannelHandler.Sharable
public class NettyRpcHandler extends SimpleChannelInboundHandler<RpcRequest> {

    private static final Logger log = LoggerFactory.getLogger(NettyRpcHandler.class);

    private final Executor businessExecutor;

    /**
     * 兼容直接构造场景。NettyTransportServer 会注入独立的有界业务线程池。
     */
    public NettyRpcHandler() {
        this(ForkJoinPool.commonPool());
    }

    public NettyRpcHandler(Executor businessExecutor) {
        this.businessExecutor = Objects.requireNonNull(businessExecutor, "businessExecutor");
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcRequest request) {
        try {
            // 反序列化、反射调用以及用户业务逻辑都可能阻塞，不能占用 Netty EventLoop。
            businessExecutor.execute(() -> processRequest(ctx, request));
        } catch (RejectedExecutionException e) {
            log.warn("RPC业务线程池已满，拒绝请求: interface={}, method={}, requestId={}",
                    request.getInterfaceName(), request.getMethodName(), request.getRequestId());
            writeErrorResponse(ctx, request, "服务器繁忙，请稍后重试");
        }
    }

    private void processRequest(ChannelHandlerContext ctx, RpcRequest request) {
        RpcResponse.Builder responseBuilder = RpcResponse.newBuilder()
                .setRequestId(request.getRequestId());

        try {
            Object serviceBean = ServiceRepository.getService(request.getInterfaceName());
            if (serviceBean == null) {
                throw new RuntimeException("未找到服务实现: " + request.getInterfaceName());
            }

            List<String> paramTypeNames = request.getParamTypesList();
            List<ByteString> paramByteList = request.getParametersList();
            if (paramTypeNames.size() != paramByteList.size()) {
                throw new IllegalArgumentException("参数类型数量与参数数量不一致");
            }

            Class<?>[] parameterTypes = new Class<?>[paramTypeNames.size()];
            Object[] parameters = new Object[paramByteList.size()];
            Serializer serializer = SerializerCode.getSerializerByCode(RpcConfig.getInstance().getSerializerCode());

            for (int i = 0; i < paramTypeNames.size(); i++) {
                Class<?> parameterType = TypeUtils.resolveClass(paramTypeNames.get(i));
                parameterTypes[i] = parameterType;

                byte[] bytes = paramByteList.get(i).toByteArray();
                Class<?> deserializeType = TypeUtils.wrapPrimitive(parameterType);
                parameters[i] = serializer.deserialize(bytes, deserializeType);
            }

            Method method = serviceBean.getClass().getMethod(request.getMethodName(), parameterTypes);
            Object result = method.invoke(serviceBean, parameters);

            byte[] resultBytes = result == null ? new byte[0] : serializer.serialize(result);
            responseBuilder.setData(ByteString.copyFrom(resultBytes));
            responseBuilder.setMessage("Success");
        } catch (Exception e) {
            Throwable cause = unwrapInvocationException(e);
            log.error("Failed to process RPC request: interface={}, method={}, requestId={}",
                    request.getInterfaceName(), request.getMethodName(), request.getRequestId(), cause);
            responseBuilder.setMessage("Error: " + safeMessage(cause));
            responseBuilder.setData(ByteString.EMPTY);
        }

        ctx.writeAndFlush(responseBuilder.build());
    }

    private void writeErrorResponse(ChannelHandlerContext ctx, RpcRequest request, String message) {
        RpcResponse response = RpcResponse.newBuilder()
                .setRequestId(request.getRequestId())
                .setMessage("Error: " + message)
                .setData(ByteString.EMPTY)
                .build();
        ctx.writeAndFlush(response);
    }

    private Throwable unwrapInvocationException(Exception e) {
        if (e instanceof InvocationTargetException) {
            Throwable target = ((InvocationTargetException) e).getTargetException();
            if (target != null) {
                return target;
            }
        }
        return e;
    }

    private String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isEmpty() ? throwable.getClass().getSimpleName() : message;
    }
}
