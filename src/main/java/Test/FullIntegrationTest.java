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
                RpcServer.main(new String[] {});
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
            String result = helloService.sayHello("World");
            System.out.println("RPC Result: " + result);

            if (!"Hello, World! (from Netty Server)".equals(result)) {
                throw new RuntimeException("Result mismatch: " + result);
            }
            System.out.println("Test Passed!");
            // System.exit(0); // Optional: relies on Daemon thread for server
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Test Failed", e);
        }
    }
}
