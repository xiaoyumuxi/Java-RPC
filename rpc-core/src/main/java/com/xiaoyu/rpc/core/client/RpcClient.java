package com.xiaoyu.rpc.core.client;

import com.xiaoyu.rpc.common.extension.ExtensionLoader;
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
import com.xiaoyu.rpc.core.registry.ServiceDiscovery;
import com.xiaoyu.rpc.core.transport.Transport;
import com.xiaoyu.rpc.core.transport.TransportClient;
import com.xiaoyu.rpc.core.util.TypeUtils;

import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class RpcClient implements AutoCloseable {

    private final TransportClient transportClient;
    private final ServiceDiscovery serviceDiscovery;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public RpcClient() {
        RpcConfig config = RpcConfig.getInstance();
        this.serviceDiscovery = ExtensionLoader.getExtensionLoader(ServiceDiscovery.class)
                .getExtension(config.getRegistryType());

        Transport transport = ExtensionLoader.getExtensionLoader(Transport.class).getExtension(config.getTransport());
        this.transportClient = transport.createClient();
    }

    RpcClient(TransportClient transportClient, ServiceDiscovery serviceDiscovery) {
        this.transportClient = Objects.requireNonNull(transportClient, "transportClient");
        this.serviceDiscovery = Objects.requireNonNull(serviceDiscovery, "serviceDiscovery");
    }

    public CompletableFuture<Object> sendRequest(RpcRequest request, Class<?> returnType) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(returnType, "returnType");

        RpcRequest preparedRequest = ensureRequestId(request);
        RpcInvocationContext context = new RpcInvocationContext(RpcSide.CLIENT, preparedRequest);
        RpcMetrics.CallTimer timer = RpcMetrics.getInstance().startCall(RpcMetricSide.CLIENT);

        if (closed.get()) {
            RpcException error = new RpcException(RpcStatusCode.CLIENT_CLOSED, "RpcClient 已关闭");
            timer.failure(error.getStatusCode());
            RpcInterceptorRegistry.onError(context, error);
            return CompletableFuture.failedFuture(error);
        }

        final RpcRequest interceptedRequest;
        try {
            interceptedRequest = RpcInterceptorRegistry.before(context, preparedRequest);
        } catch (Exception e) {
            RpcException error = toRpcException(e, RpcStatusCode.INTERNAL_ERROR, "客户端拦截器执行失败");
            timer.failure(error.getStatusCode());
            RpcInterceptorRegistry.onError(context, error);
            return CompletableFuture.failedFuture(error);
        }

        final InetSocketAddress address;
        try {
            address = serviceDiscovery.lookupService(interceptedRequest.getInterfaceName());
            if (address == null) {
                throw new RpcException(
                        RpcStatusCode.UNAVAILABLE,
                        "未发现服务: " + interceptedRequest.getInterfaceName());
            }
        } catch (Exception e) {
            RpcException error = toRpcException(e, RpcStatusCode.UNAVAILABLE,
                    "服务发现失败: " + interceptedRequest.getInterfaceName());
            timer.failure(error.getStatusCode());
            RpcInterceptorRegistry.onError(context, error);
            return CompletableFuture.failedFuture(error);
        }

        CompletableFuture<Object> resultFuture;
        try {
            CompletableFuture<Object> transportFuture = transportClient.sendRequest(interceptedRequest, address);
            resultFuture = transportFuture.thenApply(result -> handleResponse(result, returnType, context));
        } catch (Exception e) {
            resultFuture = CompletableFuture.failedFuture(e);
        }

        return resultFuture.whenComplete((result, throwable) -> {
            if (throwable == null) {
                timer.success();
            } else {
                Throwable cause = RpcException.unwrap(throwable);
                timer.failure(RpcException.statusOf(cause));
                RpcInterceptorRegistry.onError(context, cause);
            }
        });
    }

    private Object handleResponse(Object result, Class<?> returnType, RpcInvocationContext context) {
        if (!(result instanceof RpcResponse response)) {
            String actualType = result == null ? "null" : result.getClass().getName();
            throw new RpcException(RpcStatusCode.INTERNAL_ERROR, "Unexpected response type: " + actualType);
        }

        RpcStatusCode statusCode = effectiveStatus(response);
        if (statusCode != RpcStatusCode.SUCCESS) {
            throw new RpcException(
                    statusCode,
                    response.getMessage().isEmpty() ? statusCode.name() : response.getMessage(),
                    response.getErrorType());
        }

        RpcInterceptorRegistry.after(context, response);

        if (returnType == void.class || returnType == Void.class) {
            return null;
        }

        byte[] data = response.getData().toByteArray();
        Serializer serializer = SerializerCode
                .getSerializerByCode(RpcConfig.getInstance().getSerializerCode());
        Class<?> deserializeType = TypeUtils.wrapPrimitive(returnType);
        return serializer.deserialize(data, deserializeType);
    }

    private static RpcStatusCode effectiveStatus(RpcResponse response) {
        if (response.getStatusCode() != RpcStatusCode.RPC_STATUS_UNSPECIFIED) {
            return response.getStatusCode();
        }
        return "Success".equals(response.getMessage())
                ? RpcStatusCode.SUCCESS
                : RpcStatusCode.INTERNAL_ERROR;
    }

    private static RpcRequest ensureRequestId(RpcRequest request) {
        if (!request.getRequestId().isEmpty()) {
            return request;
        }
        return request.toBuilder().setRequestId(UUID.randomUUID().toString()).build();
    }

    private static RpcException toRpcException(Throwable throwable, RpcStatusCode fallback, String message) {
        Throwable cause = RpcException.unwrap(throwable);
        if (cause instanceof RpcException rpcException) {
            return rpcException;
        }
        return new RpcException(fallback, message + ": " + safeMessage(cause), cause);
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isEmpty() ? throwable.getClass().getSimpleName() : message;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        RuntimeException failure = null;
        try {
            transportClient.close();
        } catch (RuntimeException e) {
            failure = e;
        }

        try {
            serviceDiscovery.close();
        } catch (Exception e) {
            if (failure == null) {
                failure = new RuntimeException("关闭 ServiceDiscovery 失败", e);
            } else {
                failure.addSuppressed(e);
            }
        }

        if (failure != null) {
            throw failure;
        }
    }
}
