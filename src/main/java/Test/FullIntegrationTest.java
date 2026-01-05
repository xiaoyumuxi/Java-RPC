package Test;

import client.RpcClientProxy;
import service.HelloService;
import service.RpcServer;

public class FullIntegrationTest {
    public static void main(String[] args) throws InterruptedException {
        // Use Local Registry to avoid external dependency
        System.setProperty("rpc.registry", "local");

        // Start Server in a thread
        Thread serverThread = new Thread(() -> {
            try {
                RpcServer server = new RpcServer();
                // Explicitly register the service (Demonstrating the new API)
                server.register(HelloService.class, new HelloService() {
                    @Override
                    public String sayHello(String name) {
                        return "Hello, " + name + "! (from Netty Server)";
                    }
                });
                server.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();

        Thread.sleep(2000); // Wait for server start

        try {
            System.out.println("Starting Client...");
            HelloService helloService = RpcClientProxy.create(HelloService.class);

            System.out.println(">>> First Call");
            String result1 = helloService.sayHello("World1");
            System.out.println("Result1: " + result1);

            System.out.println(">>> Second Call (Should reuse connection)");
            String result2 = helloService.sayHello("World2");
            System.out.println("Result2: " + result2);

            if (!result1.contains("World1") || !result2.contains("World2")) {
                throw new RuntimeException("Results mismatch");
            }
            System.out.println("Test Passed!");
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Test Failed", e);
        }
    }
}
