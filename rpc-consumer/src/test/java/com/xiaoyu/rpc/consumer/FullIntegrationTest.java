package com.xiaoyu.rpc.consumer;

import com.xiaoyu.rpc.core.client.RpcClientProxy;
import com.xiaoyu.rpc.api.HelloService;
import com.xiaoyu.rpc.core.server.RpcServer;
import org.junit.jupiter.api.Test;
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

        Thread.sleep(5000); // Wait for server start (increased)

        try {
            System.out.println("Starting Client...");
            HelloService helloService = RpcClientProxy.create(HelloService.class);

            System.out.println(">>> First Call");
            String result1 = helloService.sayHello("World1");
            System.out.println("Result1: " + result1);

            System.out.println(">>> Second Call (Should reuse connection)");
            String result2 = helloService.sayHello("World2");
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
}
