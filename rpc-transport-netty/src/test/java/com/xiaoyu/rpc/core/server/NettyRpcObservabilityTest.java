package com.xiaoyu.rpc.core.server;

import com.google.protobuf.ByteString;
import com.xiaoyu.rpc.common.extension.ExtensionLoader;
import com.xiaoyu.rpc.common.serialization.Serializer;
import com.xiaoyu.rpc.common.vo.RpcRequest;
import com.xiaoyu.rpc.common.vo.RpcResponse;
import com.xiaoyu.rpc.common.vo.RpcStatusCode;
import com.xiaoyu.rpc.core.config.RpcConfig;
import com.xiaoyu.rpc.core.interceptor.RpcInterceptor;
import com.xiaoyu.rpc.core.interceptor.RpcInterceptorRegistry;
import com.xiaoyu.rpc.core.interceptor.RpcInvocationContext;
import com.xiaoyu.rpc.core.observability.RpcMetricSide;
import com.xiaoyu.rpc.core.observability.RpcMetrics;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RPC 服务端错误模型与可观测性测试")
class NettyRpcObservabilityTest {

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
    @DisplayName("服务不存在返回结构化 SERVICE_NOT_FOUND")
    void testServiceNotFoundStatus() {
        EmbeddedChannel channel = new EmbeddedChannel(new NettyRpcHandler(Runnable::run));
        channel.writeInbound(RpcRequest.newBuilder()
                .setRequestId("missing-1")
                .setInterfaceName("com.example.MissingService")
                .setMethodName("call")
                .build());

        RpcResponse response = channel.readOutbound();
        assertEquals(RpcStatusCode.SERVICE_NOT_FOUND, response.getStatusCode());
        assertTrue(response.getMessage().startsWith("Error:"));
        assertFalse(response.getErrorType().isEmpty());

        RpcMetrics.Snapshot snapshot = RpcMetrics.getInstance().snapshot(RpcMetricSide.SERVER);
        assertEquals(1, snapshot.totalRequests());
        assertEquals(1, snapshot.failedRequests());
        channel.finishAndReleaseAll();
    }

    @Test
    @DisplayName("业务异常返回 BUSINESS_ERROR 和远端异常类型")
    void testBusinessErrorStatus() {
        ServiceRepository.registerService(FailingService.class.getName(), new FailingServiceImpl());
        EmbeddedChannel channel = new EmbeddedChannel(new NettyRpcHandler(Runnable::run));
        channel.writeInbound(RpcRequest.newBuilder()
                .setRequestId("business-1")
                .setInterfaceName(FailingService.class.getName())
                .setMethodName("fail")
                .build());

        RpcResponse response = channel.readOutbound();
        assertEquals(RpcStatusCode.BUSINESS_ERROR, response.getStatusCode());
        assertEquals(IllegalStateException.class.getName(), response.getErrorType());
        assertTrue(response.getMessage().contains("boom"));
        channel.finishAndReleaseAll();
    }

    @Test
    @DisplayName("服务端拦截器收到 before/after 回调并记录成功指标")
    void testServerInterceptorAndMetrics() {
        ServiceRepository.registerService(EchoService.class.getName(), new EchoServiceImpl());
        AtomicInteger beforeCount = new AtomicInteger();
        AtomicInteger afterCount = new AtomicInteger();
        RpcInterceptorRegistry.register(new RpcInterceptor() {
            @Override
            public RpcRequest before(RpcInvocationContext context, RpcRequest request) {
                assertEquals("trace-123", request.getMetadataOrThrow("trace-id"));
                beforeCount.incrementAndGet();
                return request;
            }

            @Override
            public void after(RpcInvocationContext context, RpcResponse response) {
                afterCount.incrementAndGet();
            }
        });

        EmbeddedChannel channel = new EmbeddedChannel(new NettyRpcHandler(Runnable::run));
        channel.writeInbound(RpcRequest.newBuilder()
                .setRequestId("echo-1")
                .setInterfaceName(EchoService.class.getName())
                .setMethodName("echo")
                .addParamTypes(String.class.getName())
                .addParameters(ByteString.copyFrom(serializer.serialize("hello")))
                .putMetadata("trace-id", "trace-123")
                .build());

        RpcResponse response = channel.readOutbound();
        assertEquals(RpcStatusCode.SUCCESS, response.getStatusCode());
        assertEquals(1, beforeCount.get());
        assertEquals(1, afterCount.get());

        RpcMetrics.Snapshot snapshot = RpcMetrics.getInstance().snapshot(RpcMetricSide.SERVER);
        assertEquals(1, snapshot.successRequests());
        assertEquals(0, snapshot.activeRequests());
        channel.finishAndReleaseAll();
    }

    @Test
    @DisplayName("业务线程池拒绝请求返回 SERVER_BUSY")
    void testServerBusyStatus() {
        EmbeddedChannel channel = new EmbeddedChannel(new NettyRpcHandler(command -> {
            throw new RejectedExecutionException("full");
        }));
        channel.writeInbound(RpcRequest.newBuilder()
                .setRequestId("busy-1")
                .setInterfaceName("com.example.AnyService")
                .setMethodName("call")
                .build());

        RpcResponse response = channel.readOutbound();
        assertEquals(RpcStatusCode.SERVER_BUSY, response.getStatusCode());
        assertEquals(1, RpcMetrics.getInstance().snapshot(RpcMetricSide.SERVER).failedRequests());
        channel.finishAndReleaseAll();
    }

    public interface FailingService {
        String fail();
    }

    public static class FailingServiceImpl implements FailingService {
        @Override
        public String fail() {
            throw new IllegalStateException("boom");
        }
    }

    public interface EchoService {
        String echo(String value);
    }

    public static class EchoServiceImpl implements EchoService {
        @Override
        public String echo(String value) {
            return value;
        }
    }

    private static void resetRpcConfigSingleton() throws Exception {
        Field field = RpcConfig.class.getDeclaredField("instance");
        field.setAccessible(true);
        field.set(null, null);
    }
}
