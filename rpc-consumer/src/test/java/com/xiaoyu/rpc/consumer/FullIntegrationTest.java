package com.xiaoyu.rpc.consumer;

import com.google.protobuf.ByteString;
import com.xiaoyu.rpc.api.HelloService;
import com.xiaoyu.rpc.common.serialization.Serializer;
import com.xiaoyu.rpc.common.serialization.SerializerCode;
import com.xiaoyu.rpc.common.vo.RpcRequest;
import com.xiaoyu.rpc.core.client.RpcClient;
import com.xiaoyu.rpc.core.config.RpcConfig;
import com.xiaoyu.rpc.core.server.RpcServer;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class FullIntegrationTest {

    public static class HelloServiceImpl implements HelloService {
        @Override
        public String sayHello(String name) {
            return "Hello, " + name + "! (from Netty Server)";
        }
    }

    @Test
    public void testFullIntegration() throws Exception {
        String registry = System.getProperty("rpc.registry", "local");
        String protocol = System.getProperty("rpc.protocol", "netty");
        String serializerName = System.getProperty("rpc.serializer", "kryo");
        int port = findFreePort();

        System.setProperty("rpc.registry", registry);
        System.setProperty("rpc.server-host", "127.0.0.1");
        System.setProperty("rpc.server-port", String.valueOf(port));
        System.setProperty("rpc.serializer", serializerName);
        System.setProperty("rpc.transport", "netty");
        System.setProperty("rpc.protocol", protocol);

        AtomicReference<Throwable> serverFailure = new AtomicReference<>();
        Thread serverThread = new Thread(() -> {
            try {
                RpcServer server = new RpcServer();
                server.register(HelloService.class, new HelloServiceImpl());
                server.start();
            } catch (Throwable e) {
                serverFailure.set(e);
            }
        }, "rpc-integration-server");
        serverThread.setDaemon(true);
        serverThread.start();

        waitForServer(port, serverFailure);

        try {
            RpcClient rpcClient = new RpcClient();
            Serializer serializer = SerializerCode.getSerializerByCode(RpcConfig.getInstance().getSerializerCode());

            String result1 = (String) rpcClient
                    .sendRequest(buildRequest("World1", serializer), String.class)
                    .get(5, TimeUnit.SECONDS);

            String result2 = (String) rpcClient
                    .sendRequest(buildRequest("World2", serializer), String.class)
                    .get(5, TimeUnit.SECONDS);

            assertNotNull(result1, "Result1 should not be null");
            assertNotNull(result2, "Result2 should not be null");
            assertTrue(result1.contains("World1"), "Result1 should contain World1");
            assertTrue(result2.contains("World2"), "Result2 should contain World2");
        } finally {
            System.clearProperty("rpc.server-host");
            System.clearProperty("rpc.server-port");
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

    private static int findFreePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            return socket.getLocalPort();
        }
    }

    private static void waitForServer(int port, AtomicReference<Throwable> serverFailure) throws Exception {
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        Throwable lastFailure = null;

        while (System.nanoTime() < deadlineNanos) {
            Throwable startupFailure = serverFailure.get();
            if (startupFailure != null) {
                fail("RPC server failed during startup", startupFailure);
            }

            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("127.0.0.1", port), 200);
                return;
            } catch (Exception e) {
                lastFailure = e;
                Thread.sleep(100);
            }
        }

        fail("RPC server did not become ready on port " + port, lastFailure);
    }
}
