package com.xiaoyu.rpc.core.client;

import com.google.protobuf.ByteString;
import com.xiaoyu.rpc.common.extension.ExtensionLoader;
import com.xiaoyu.rpc.common.serialization.Serializer;
import com.xiaoyu.rpc.common.vo.RpcRequest;
import com.xiaoyu.rpc.common.vo.RpcResponse;
import com.xiaoyu.rpc.common.vo.RpcStatusCode;
import com.xiaoyu.rpc.core.config.RpcConfig;
import com.xiaoyu.rpc.core.exception.RpcException;
import com.xiaoyu.rpc.core.interceptor.RpcInterceptor;
import com.xiaoyu.rpc.core.interceptor.RpcInterceptorRegistry;
import com.xiaoyu.rpc.core.interceptor.RpcInvocationContext;
import com.xiaoyu.rpc.core.observability.RpcMetricSide;
import com.xiaoyu.rpc.core.observability.RpcMetrics;
import com.xiaoyu.rpc.core.registry.ServiceDiscovery;
import com.xiaoyu.rpc.core.transport.TransportClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RPC 可观测性与拦截器测试")
class RpcObservabilityTest {

    private Serializer serializer;

    @BeforeEach
    void setUp() throws Exception {
        System.setProperty("rpc.registry", "local");
        System.setProperty("rpc.serializer", "java");
        resetRpcConfigSingleton();
        serializer = ExtensionLoader.getExtensionLoader(Serializer.class).getExtension("java");
        RpcInterceptorRegistry.clear();
        RpcMetrics.getInstance().reset();
    }

    @AfterEach
    void tearDown() throws Exception {
        RpcInterceptorRegistry.clear();
        RpcMetrics.getInstance().reset();
        System.clearProperty("rpc.registry");
        System.clearProperty("rpc.serializer");
        resetRpcConfigSingleton();
    }

    @Test
    @DisplayName("客户端拦截器按顺序执行并可注入 metadata")
    void testClientInterceptorOrderingAndMetadata() throws Exception {
        List<String> callbacks = new ArrayList<>();
        AtomicReference<RpcRequest> sentRequest = new AtomicReference<>();

        RpcInterceptorRegistry.register(interceptor("late", 20, callbacks));
        RpcInterceptorRegistry.register(interceptor("early", 10, callbacks));

        TransportClient transportClient = (request, address) -> {
            sentRequest.set(request);
            return CompletableFuture.completedFuture(successResponse(request, "ok"));
        };
        ServiceDiscovery discovery = serviceName -> new InetSocketAddress("127.0.0.1", 8080);

        try (RpcClient client = new RpcClient(transportClient, discovery)) {
            Object result = client.sendRequest(request(), String.class).get(1, TimeUnit.SECONDS);
            assertEquals("ok", result);
        }

        assertEquals(List.of("before-early", "before-late", "after-late", "after-early"), callbacks);
        assertEquals("early", sentRequest.get().getMetadataOrThrow("early"));
        assertEquals("late", sentRequest.get().getMetadataOrThrow("late"));
        assertFalse(sentRequest.get().getRequestId().isEmpty());

        RpcMetrics.Snapshot snapshot = RpcMetrics.getInstance().snapshot(RpcMetricSide.CLIENT);
        assertEquals(1, snapshot.totalRequests());
        assertEquals(1, snapshot.successRequests());
        assertEquals(0, snapshot.failedRequests());
        assertEquals(0, snapshot.activeRequests());
    }

    @Test
    @DisplayName("结构化错误转换为 RpcException 并计入失败指标")
    void testStructuredErrorAndFailureMetrics() throws Exception {
        AtomicReference<Throwable> interceptedError = new AtomicReference<>();
        RpcInterceptorRegistry.register(new RpcInterceptor() {
            @Override
            public void onError(RpcInvocationContext context, Throwable error) {
                interceptedError.set(error);
            }
        });

        TransportClient transportClient = (request, address) -> CompletableFuture.completedFuture(
                RpcResponse.newBuilder()
                        .setRequestId(request.getRequestId())
                        .setStatusCode(RpcStatusCode.BUSINESS_ERROR)
                        .setMessage("Error: boom")
                        .setErrorType(IllegalStateException.class.getName())
                        .build());
        ServiceDiscovery discovery = serviceName -> new InetSocketAddress("127.0.0.1", 8080);

        try (RpcClient client = new RpcClient(transportClient, discovery)) {
            ExecutionException exception = assertThrows(
                    ExecutionException.class,
                    () -> client.sendRequest(request(), String.class).get(1, TimeUnit.SECONDS));
            assertInstanceOf(RpcException.class, exception.getCause());
            RpcException rpcException = (RpcException) exception.getCause();
            assertEquals(RpcStatusCode.BUSINESS_ERROR, rpcException.getStatusCode());
            assertEquals(IllegalStateException.class.getName(), rpcException.getRemoteErrorType());
        }

        assertInstanceOf(RpcException.class, interceptedError.get());
        RpcMetrics.Snapshot snapshot = RpcMetrics.getInstance().snapshot(RpcMetricSide.CLIENT);
        assertEquals(1, snapshot.totalRequests());
        assertEquals(0, snapshot.successRequests());
        assertEquals(1, snapshot.failedRequests());
    }

    @Test
    @DisplayName("超时错误单独计入 timeout 指标")
    void testTimeoutMetrics() throws Exception {
        TransportClient transportClient = (request, address) -> CompletableFuture.failedFuture(
                new RpcException(RpcStatusCode.TIMEOUT, "timeout"));
        ServiceDiscovery discovery = serviceName -> new InetSocketAddress("127.0.0.1", 8080);

        try (RpcClient client = new RpcClient(transportClient, discovery)) {
            assertThrows(
                    ExecutionException.class,
                    () -> client.sendRequest(request(), String.class).get(1, TimeUnit.SECONDS));
        }

        RpcMetrics.Snapshot snapshot = RpcMetrics.getInstance().snapshot(RpcMetricSide.CLIENT);
        assertEquals(1, snapshot.failedRequests());
        assertEquals(1, snapshot.timeoutRequests());
    }

    private RpcInterceptor interceptor(String name, int order, List<String> callbacks) {
        return new RpcInterceptor() {
            @Override
            public int order() {
                return order;
            }

            @Override
            public RpcRequest before(RpcInvocationContext context, RpcRequest request) {
                callbacks.add("before-" + name);
                return request.toBuilder().putMetadata(name, name).build();
            }

            @Override
            public void after(RpcInvocationContext context, RpcResponse response) {
                callbacks.add("after-" + name);
            }
        };
    }

    private RpcResponse successResponse(RpcRequest request, String value) {
        return RpcResponse.newBuilder()
                .setRequestId(request.getRequestId())
                .setStatusCode(RpcStatusCode.SUCCESS)
                .setMessage("Success")
                .setData(ByteString.copyFrom(serializer.serialize(value)))
                .build();
    }

    private static RpcRequest request() {
        return RpcRequest.newBuilder()
                .setInterfaceName("com.example.DemoService")
                .setMethodName("ping")
                .build();
    }

    private static void resetRpcConfigSingleton() throws Exception {
        Field field = RpcConfig.class.getDeclaredField("instance");
        field.setAccessible(true);
        field.set(null, null);
    }
}
