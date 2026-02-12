package com.xiaoyu.rpc.consumer;

import com.xiaoyu.rpc.api.HelloService;
import com.xiaoyu.rpc.common.serialization.Serializer;
import com.xiaoyu.rpc.common.serialization.SerializerCode;
import com.xiaoyu.rpc.common.vo.RpcRequest;
import com.xiaoyu.rpc.core.client.RpcClient;
import com.xiaoyu.rpc.core.config.RpcConfig;
import com.xiaoyu.rpc.core.server.RpcServer;
import com.google.protobuf.ByteString;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class FullIntegrationTest {

    // Make the implementation public for reflection access
    public static class HelloServiceImpl implements HelloService {
        @Override
        public String sayHello(String name) {
            return "Hello, " + name + "! (from Netty Server)";
        }
    }

    @Test
    public void testFullIntegration() throws InterruptedException {
        // Use Local Registry to avoid external dependency
        System.setProperty("rpc.registry", "local");
        System.setProperty("rpc.server-port", "9090"); // Use port 9090
        // Ensure we use KRYO or JSON/Hessian serializer that supports mundane Java
        // classes
        System.setProperty("rpc.serializer", "kryo");
        System.setProperty("rpc.transport", "netty");

        // 沙箱或受限环境无法监听端口时，跳过此集成测试，避免把环境问题算成代码失败
        Assumptions.assumeTrue(canBindLocalPort(9090), "No permission to bind local test port 9090");

        // 当前默认配置可能是 grpc，但客户端泛化 grpc 尚未支持，测试里强制切到 netty
        forceConfig("protocol", "netty");

        // Start Server in a thread
        Thread serverThread = new Thread(() -> {
            try {
                RpcServer server = new RpcServer();
                // Explicitly register the service (Demonstrating the new API)
                server.register(HelloService.class, new HelloServiceImpl());
                server.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();

        Thread.sleep(1500); // Wait for server start

        try {
            System.out.println("Starting Client...");
            RpcClient rpcClient = new RpcClient();
            Serializer serializer = SerializerCode.getSerializerByCode(RpcConfig.getInstance().getSerializerCode());

            System.out.println(">>> First Call");
            String result1 = (String) rpcClient
                    .sendRequest(buildRequest("World1", serializer), String.class)
                    .get(5, TimeUnit.SECONDS);
            System.out.println("Result1: " + result1);

            System.out.println(">>> Second Call (Should reuse connection)");
            String result2 = (String) rpcClient
                    .sendRequest(buildRequest("World2", serializer), String.class)
                    .get(5, TimeUnit.SECONDS);
            System.out.println("Result2: " + result2);

            assertNotNull(result1, "Result1 should not be null");
            assertNotNull(result1, "Result2 should not be null");
            assertTrue(result1.contains("World1"), "Result1 should contain World1");
            assertTrue(result2.contains("World2"), "Result2 should contain World2");

            System.out.println("Test Passed!");
        } catch (Exception e) {
            e.printStackTrace();
            fail("Test failed with exception: " + e.getMessage());
        }
    }

    private static RpcRequest buildRequest(String name, Serializer serializer) {
        byte[] argBytes = serializer.serialize(name);
        return RpcRequest.newBuilder()
                .setInterfaceName(HelloService.class.getName())
                .setMethodName("sayHello")
                .addParamTypes(String.class.getName())
                .addParameters(ByteString.copyFrom(argBytes))
                .build();
    }

    private static boolean canBindLocalPort(int port) {
        try (ServerSocket ignored = new ServerSocket(port, 1, InetAddress.getByName("127.0.0.1"))) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void forceConfig(String fieldName, Object value) {
        try {
            RpcConfig config = RpcConfig.getInstance();
            Field field = RpcConfig.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(config, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to force RpcConfig field: " + fieldName, e);
        }
    }
}
