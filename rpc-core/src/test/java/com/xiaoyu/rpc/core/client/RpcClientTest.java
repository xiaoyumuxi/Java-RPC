package com.xiaoyu.rpc.core.client;

import com.google.protobuf.ByteString;
import com.xiaoyu.rpc.common.extension.ExtensionLoader;
import com.xiaoyu.rpc.common.serialization.Serializer;
import com.xiaoyu.rpc.common.vo.RpcRequest;
import com.xiaoyu.rpc.common.vo.RpcResponse;
import com.xiaoyu.rpc.core.config.RpcConfig;
import com.xiaoyu.rpc.core.registry.ServiceDiscovery;
import com.xiaoyu.rpc.core.transport.TransportClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RpcClient 异常与边界测试")
public class RpcClientTest {

    @BeforeEach
    void setUp() throws Exception {
        System.setProperty("rpc.registry", "local");
        System.setProperty("rpc.serializer", "java");
        resetRpcConfigSingleton();
    }

    @AfterEach
    void tearDown() throws Exception {
        System.clearProperty("rpc.registry");
        System.clearProperty("rpc.serializer");
        resetRpcConfigSingleton();
    }

    @Test
    @DisplayName("服务发现为空时返回异常 Future")
    void testServiceNotFound() {
        TransportClient transportClient = (request, address) -> CompletableFuture.completedFuture(null);
        ServiceDiscovery serviceDiscovery = serviceName -> null;
        RpcClient rpcClient = new RpcClient(transportClient, serviceDiscovery);

        CompletableFuture<Object> future = rpcClient.sendRequest(minimalRequest(), String.class);

        assertTrue(future.isCompletedExceptionally());
        ExecutionException ex = assertThrows(ExecutionException.class, () -> future.get(1, TimeUnit.SECONDS));
        assertTrue(ex.getCause().getMessage().contains("未发现服务"));
    }

    @Test
    @DisplayName("传输层抛异常时返回异常 Future")
    void testTransportThrows() {
        TransportClient transportClient = (request, address) -> {
            throw new RuntimeException("transport down");
        };
        ServiceDiscovery serviceDiscovery = serviceName -> new InetSocketAddress("127.0.0.1", 8080);
        RpcClient rpcClient = new RpcClient(transportClient, serviceDiscovery);

        CompletableFuture<Object> future = rpcClient.sendRequest(minimalRequest(), String.class);

        assertTrue(future.isCompletedExceptionally());
        ExecutionException ex = assertThrows(ExecutionException.class, () -> future.get(1, TimeUnit.SECONDS));
        assertTrue(ex.getCause().getMessage().contains("transport down"));
    }

    @Test
    @DisplayName("返回非 RpcResponse 类型时应失败")
    void testUnexpectedResponseType() {
        TransportClient transportClient = (request, address) -> CompletableFuture.completedFuture("not-rpc-response");
        ServiceDiscovery serviceDiscovery = serviceName -> new InetSocketAddress("127.0.0.1", 8080);
        RpcClient rpcClient = new RpcClient(transportClient, serviceDiscovery);

        CompletableFuture<Object> future = rpcClient.sendRequest(minimalRequest(), String.class);

        assertTrue(future.isCompletedExceptionally());
        ExecutionException ex = assertThrows(ExecutionException.class, () -> future.get(1, TimeUnit.SECONDS));
        assertTrue(ex.getCause().getMessage().contains("Unexpected response type"));
    }

    @Test
    @DisplayName("RpcResponse 正常反序列化返回目标类型")
    void testSuccessfulDeserialize() throws Exception {
        Serializer serializer = ExtensionLoader.getExtensionLoader(Serializer.class).getExtension("java");
        RpcResponse response = successfulResponse(serializer.serialize("hello"));

        TransportClient transportClient = (request, address) -> CompletableFuture.completedFuture(response);
        ServiceDiscovery serviceDiscovery = serviceName -> new InetSocketAddress("127.0.0.1", 8080);
        RpcClient rpcClient = new RpcClient(transportClient, serviceDiscovery);

        Object result = rpcClient.sendRequest(minimalRequest(), String.class).get(1, TimeUnit.SECONDS);
        assertEquals("hello", result);
    }

    @Test
    @DisplayName("基本类型返回值使用包装类型完成反序列化")
    void testPrimitiveReturnTypeDeserialize() throws Exception {
        Serializer serializer = ExtensionLoader.getExtensionLoader(Serializer.class).getExtension("java");
        RpcResponse response = successfulResponse(serializer.serialize(42));

        TransportClient transportClient = (request, address) -> CompletableFuture.completedFuture(response);
        ServiceDiscovery serviceDiscovery = serviceName -> new InetSocketAddress("127.0.0.1", 8080);
        RpcClient rpcClient = new RpcClient(transportClient, serviceDiscovery);

        Object result = rpcClient.sendRequest(minimalRequest(), int.class).get(1, TimeUnit.SECONDS);
        assertEquals(42, result);
    }

    @Test
    @DisplayName("void 返回类型不尝试反序列化空响应体")
    void testVoidReturnType() throws Exception {
        RpcResponse response = successfulResponse(new byte[0]);
        TransportClient transportClient = (request, address) -> CompletableFuture.completedFuture(response);
        ServiceDiscovery serviceDiscovery = serviceName -> new InetSocketAddress("127.0.0.1", 8080);
        RpcClient rpcClient = new RpcClient(transportClient, serviceDiscovery);

        Object result = rpcClient.sendRequest(minimalRequest(), void.class).get(1, TimeUnit.SECONDS);
        assertNull(result);
    }

    private static RpcResponse successfulResponse(byte[] body) {
        return RpcResponse.newBuilder()
                .setRequestId("req-1")
                .setMessage("Success")
                .setData(ByteString.copyFrom(body))
                .build();
    }

    private static RpcRequest minimalRequest() {
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
