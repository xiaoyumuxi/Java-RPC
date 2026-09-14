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

import java.io.Serializable;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("异步 RPC 泛型返回值测试")
class ProxyAsyncReturnTypeTest {

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
    @DisplayName("JDK Proxy 按 CompletableFuture<T> 的 T 反序列化")
    void testJdkProxyAsyncPayloadType() throws Exception {
        RpcClient rpcClient = rpcClientReturning(new TestUser("alice"));
        AsyncUserService proxy = new JdkProxyFactory(rpcClient).getProxy(AsyncUserService.class);

        TestUser user = proxy.findUser("alice").get(1, TimeUnit.SECONDS);

        assertEquals(new TestUser("alice"), user);
    }

    @Test
    @DisplayName("ByteBuddy Proxy 按 CompletableFuture<T> 的 T 反序列化")
    void testByteBuddyProxyAsyncPayloadType() throws Exception {
        RpcClient rpcClient = rpcClientReturning(new TestUser("bob"));
        AsyncUserService proxy = new ByteBuddyProxyFactory(rpcClient).getProxy(AsyncUserService.class);

        TestUser user = proxy.findUser("bob").get(1, TimeUnit.SECONDS);

        assertEquals(new TestUser("bob"), user);
    }

    @Test
    @DisplayName("嵌套参数化异步返回值明确拒绝而不是错误反序列化")
    void testRejectNestedParameterizedAsyncReturnType() {
        RpcClient rpcClient = rpcClientReturning(new TestUser("unused"));
        NestedAsyncService proxy = new JdkProxyFactory(rpcClient).getProxy(NestedAsyncService.class);

        assertThrows(IllegalArgumentException.class, proxy::findUsers);
    }

    private static RpcClient rpcClientReturning(Object value) {
        Serializer serializer = ExtensionLoader.getExtensionLoader(Serializer.class).getExtension("java");
        TransportClient transportClient = (request, address) -> {
            RpcResponse response = RpcResponse.newBuilder()
                    .setRequestId(request.getRequestId())
                    .setMessage("Success")
                    .setData(ByteString.copyFrom(serializer.serialize(value)))
                    .build();
            return CompletableFuture.completedFuture(response);
        };
        ServiceDiscovery discovery = serviceName -> new InetSocketAddress("127.0.0.1", 8080);
        return new RpcClient(transportClient, discovery);
    }

    interface AsyncUserService {
        CompletableFuture<TestUser> findUser(String name);
    }

    interface NestedAsyncService {
        CompletableFuture<List<TestUser>> findUsers();
    }

    static final class TestUser implements Serializable {
        private final String name;

        TestUser(String name) {
            this.name = name;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof TestUser)) {
                return false;
            }
            TestUser other = (TestUser) obj;
            return Objects.equals(name, other.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name);
        }
    }

    private static void resetRpcConfigSingleton() throws Exception {
        Field field = RpcConfig.class.getDeclaredField("instance");
        field.setAccessible(true);
        field.set(null, null);
    }
}
