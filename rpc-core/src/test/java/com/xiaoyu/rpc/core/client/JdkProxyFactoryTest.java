package com.xiaoyu.rpc.core.client;

import com.google.protobuf.ByteString;
import com.xiaoyu.rpc.common.extension.ExtensionLoader;
import com.xiaoyu.rpc.common.serialization.Serializer;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("JdkProxyFactory 客户端复用测试")
class JdkProxyFactoryTest {

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
    @DisplayName("同一个代理的多次调用复用注入的 RpcClient")
    void testReuseRpcClientAcrossInvocations() throws Exception {
        Serializer serializer = ExtensionLoader.getExtensionLoader(Serializer.class).getExtension("java");
        AtomicInteger requestCount = new AtomicInteger();

        TransportClient transportClient = (request, address) -> {
            requestCount.incrementAndGet();
            RpcResponse response = RpcResponse.newBuilder()
                    .setRequestId(request.getRequestId())
                    .setMessage("Success")
                    .setData(ByteString.copyFrom(serializer.serialize("ok")))
                    .build();
            return CompletableFuture.completedFuture(response);
        };
        ServiceDiscovery serviceDiscovery = serviceName -> new InetSocketAddress("127.0.0.1", 8080);
        RpcClient rpcClient = new RpcClient(transportClient, serviceDiscovery);

        EchoService proxy = new JdkProxyFactory(rpcClient).getProxy(EchoService.class);

        assertEquals("ok", proxy.echo("first"));
        assertEquals("ok", proxy.echo("second"));
        assertEquals(2, requestCount.get());
    }

    interface EchoService {
        String echo(String value);
    }

    private static void resetRpcConfigSingleton() throws Exception {
        Field field = RpcConfig.class.getDeclaredField("instance");
        field.setAccessible(true);
        field.set(null, null);
    }
}
