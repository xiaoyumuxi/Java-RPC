package com.xiaoyu.rpc.core.server;

import com.google.protobuf.ByteString;
import com.xiaoyu.rpc.common.serialization.Serializer;
import com.xiaoyu.rpc.common.serialization.SerializerCode;
import com.xiaoyu.rpc.common.vo.RpcRequest;
import com.xiaoyu.rpc.common.vo.RpcResponse;
import com.xiaoyu.rpc.common.vo.RpcStatusCode;
import com.xiaoyu.rpc.core.config.RpcConfig;
import com.xiaoyu.rpc.core.exception.RpcException;
import com.xiaoyu.rpc.core.interceptor.RpcInterceptorRegistry;
import com.xiaoyu.rpc.core.interceptor.RpcInvocationContext;
import com.xiaoyu.rpc.core.interceptor.RpcSide;
import com.xiaoyu.rpc.core.observability.RpcMetricSide;
import com.xiaoyu.rpc.core.observability.RpcMetrics;
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

    public NettyRpcHandler() {
        this(ForkJoinPool.commonPool());
    }

    public NettyRpcHandler(Executor businessExecutor) {
        this.businessExecutor = Objects.requireNonNull(businessExecutor, "businessExecutor");
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcRequest request) {
        RpcMetrics.CallTimer timer = RpcMetrics.getInstance().startCall(RpcMetricSide.SERVER);
        RpcInvocationContext context = new RpcInvocationContext(RpcSide.SERVER, request);
        try {
            businessExecutor.execute(() -> processRequest(ctx, request, context, timer));
        } catch (RejectedExecutionException e) {
            log.warn("RPC业务线程池已满，拒绝请求: interface={}, method={}, requestId={}",
                    request.getInterfaceName(), request.getMethodName(), request.getRequestId());
            RpcException error = new RpcException(RpcStatusCode.SERVER_BUSY, "服务器繁忙，请稍后重试", e);
            timer.failure(error.getStatusCode());
            RpcInterceptorRegistry.onError(context, error);
            writeErrorResponse(ctx, request, error.getStatusCode(), safeMessage(error), e.getClass().getName());
        }
    }

    private void processRequest(ChannelHandlerContext ctx, RpcRequest originalRequest,
            RpcInvocationContext context, RpcMetrics.CallTimer timer) {
        RpcRequest request = originalRequest;
        try {
            request = RpcInterceptorRegistry.before(context, request);

            Object serviceBean = ServiceRepository.getService(request.getInterfaceName());
            if (serviceBean == null) {
                throw new RpcException(
                        RpcStatusCode.SERVICE_NOT_FOUND,
                        "未找到服务实现: " + request.getInterfaceName());
            }

            List<String> paramTypeNames = request.getParamTypesList();
            List<ByteString> paramByteList = request.getParametersList();
            if (paramTypeNames.size() != paramByteList.size()) {
                throw new RpcException(RpcStatusCode.INVALID_ARGUMENT, "参数类型数量与参数数量不一致");
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

            Method method;
            try {
                method = serviceBean.getClass().getMethod(request.getMethodName(), parameterTypes);
            } catch (NoSuchMethodException e) {
                throw new RpcException(
                        RpcStatusCode.METHOD_NOT_FOUND,
                        "未找到服务方法: " + request.getMethodName(),
                        e);
            }

            Object result;
            try {
                result = method.invoke(serviceBean, parameters);
            } catch (InvocationTargetException e) {
                Throwable target = e.getTargetException() == null ? e : e.getTargetException();
                if (target instanceof RpcException rpcException) {
                    throw rpcException;
                }
                throw new RpcException(
                        RpcStatusCode.BUSINESS_ERROR,
                        safeMessage(target),
                        target.getClass().getName(),
                        target);
            }

            byte[] resultBytes = result == null ? new byte[0] : serializer.serialize(result);
            RpcResponse response = RpcResponse.newBuilder()
                    .setRequestId(request.getRequestId())
                    .setData(ByteString.copyFrom(resultBytes))
                    .setStatusCode(RpcStatusCode.SUCCESS)
                    .setMessage("Success")
                    .build();

            RpcInterceptorRegistry.after(context, response);
            timer.success();
            ctx.writeAndFlush(response);
        } catch (Exception e) {
            RpcException error = normalizeError(e);
            log.error("Failed to process RPC request: interface={}, method={}, requestId={}, status={}",
                    request.getInterfaceName(), request.getMethodName(), request.getRequestId(),
                    error.getStatusCode(), error);
            timer.failure(error.getStatusCode());
            RpcInterceptorRegistry.onError(context, error);
            writeErrorResponse(
                    ctx,
                    request,
                    error.getStatusCode(),
                    error.getMessage(),
                    error.getRemoteErrorType().isEmpty()
                            ? RpcException.unwrap(error).getClass().getName()
                            : error.getRemoteErrorType());
        }
    }

    private RpcException normalizeError(Exception error) {
        if (error instanceof RpcException rpcException) {
            return rpcException;
        }
        if (error instanceof IllegalArgumentException) {
            return new RpcException(
                    RpcStatusCode.INVALID_ARGUMENT,
                    safeMessage(error),
                    error.getClass().getName(),
                    error);
        }
        return new RpcException(
                RpcStatusCode.INTERNAL_ERROR,
                safeMessage(error),
                error.getClass().getName(),
                error);
    }

    private void writeErrorResponse(ChannelHandlerContext ctx, RpcRequest request,
            RpcStatusCode statusCode, String message, String errorType) {
        RpcResponse response = RpcResponse.newBuilder()
                .setRequestId(request.getRequestId())
                .setStatusCode(statusCode)
                .setMessage("Error: " + message)
                .setErrorType(errorType == null ? "" : errorType)
                .setData(ByteString.EMPTY)
                .build();
        ctx.writeAndFlush(response);
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isEmpty() ? throwable.getClass().getSimpleName() : message;
    }
}
