package Test;

import extension.ExtensionLoader;
import loadbalancer.LoadBalancer;
import java.util.Arrays;
import java.util.List;

public class LoadBalancerTest {
    public static void main(String[] args) {
        System.out.println("--- 测试 LoadBalancer SPI ---");

        // 1. 测试 SPI 机制是否能加载
        LoadBalancer randomLb = ExtensionLoader.getExtensionLoader(LoadBalancer.class).getExtension("random");
        LoadBalancer roundRobinLb = ExtensionLoader.getExtensionLoader(LoadBalancer.class).getExtension("roundrobin");

        System.out.println("Random LB Loaded: " + (randomLb != null));
        System.out.println("RoundRobin LB Loaded: " + (roundRobinLb != null));

        List<String> servers = Arrays.asList("127.0.0.1:8080", "127.0.0.1:8081", "127.0.0.1:8082");

        // 2. 测试 RoundRobin
        System.out.println("\n--- 测试 RoundRobin ---");
        for (int i = 0; i < 5; i++) {
            System.out.println("Select: " + roundRobinLb.select(servers));
        }

        // 3. 测试 Random
        System.out.println("\n--- 测试 Random ---");
        for (int i = 0; i < 5; i++) {
            System.out.println("Select: " + randomLb.select(servers));
        }
    }
}
