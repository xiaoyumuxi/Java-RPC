package com.xiaoyu.rpc.core.loadbalancer;

import com.xiaoyu.rpc.common.extension.ExtensionLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 负载均衡器单元测试
 */
@DisplayName("LoadBalancer 负载均衡器测试")
public class LoadBalancerTest {

    private final List<String> servers = Arrays.asList(
            "127.0.0.1:8080",
            "127.0.0.1:8081",
            "127.0.0.1:8082");

    @Test
    @DisplayName("测试 Random 负载均衡器加载")
    void testRandomLoadBalancerLoading() {
        LoadBalancer lb = ExtensionLoader.getExtensionLoader(LoadBalancer.class).getExtension("random");
        assertNotNull(lb, "Random load balancer should be loaded");
        assertTrue(lb instanceof RandomLoadBalancer, "Should be instance of RandomLoadBalancer");
    }

    @Test
    @DisplayName("测试 RoundRobin 负载均衡器加载")
    void testRoundRobinLoadBalancerLoading() {
        LoadBalancer lb = ExtensionLoader.getExtensionLoader(LoadBalancer.class).getExtension("roundrobin");
        assertNotNull(lb, "RoundRobin load balancer should be loaded");
        assertTrue(lb instanceof RoundRobinLoadBalancer, "Should be instance of RoundRobinLoadBalancer");
    }

    @Test
    @DisplayName("测试 Random 负载均衡器选择服务器")
    void testRandomLoadBalancerSelect() {
        LoadBalancer lb = ExtensionLoader.getExtensionLoader(LoadBalancer.class).getExtension("random");

        // 多次选择确保都在列表中
        for (int i = 0; i < 10; i++) {
            String selected = lb.select(servers);
            assertNotNull(selected, "Selected server should not be null");
            assertTrue(servers.contains(selected), "Selected server should be in the list");
        }
    }

    @Test
    @DisplayName("测试 RoundRobin 负载均衡器轮询")
    void testRoundRobinLoadBalancerSelect() {
        // 创建新的 RoundRobin 实例来确保从头开始
        LoadBalancer lb = new RoundRobinLoadBalancer();

        // 测试轮询模式
        String first = lb.select(servers);
        String second = lb.select(servers);
        String third = lb.select(servers);
        String fourth = lb.select(servers); // 应该回到第一个

        assertNotNull(first, "First selection should not be null");
        assertNotNull(second, "Second selection should not be null");
        assertNotNull(third, "Third selection should not be null");
        assertNotNull(fourth, "Fourth selection should not be null");

        // 确保四次选择覆盖了所有服务器
        Set<String> selected = new HashSet<>(Arrays.asList(first, second, third));
        assertEquals(3, selected.size(), "RoundRobin should cycle through all 3 servers");

        // 第四次选择应该与前三次之一相同（循环）
        assertTrue(servers.contains(fourth), "Fourth selection should be in server list");
    }

    @Test
    @DisplayName("测试负载均衡器处理单节点列表")
    void testLoadBalancerWithSingleServer() {
        LoadBalancer randomLb = ExtensionLoader.getExtensionLoader(LoadBalancer.class).getExtension("random");
        LoadBalancer rrLb = ExtensionLoader.getExtensionLoader(LoadBalancer.class).getExtension("roundrobin");

        List<String> singleServer = Arrays.asList("127.0.0.1:9999");

        assertEquals("127.0.0.1:9999", randomLb.select(singleServer), "Random should return the only server");
        assertEquals("127.0.0.1:9999", rrLb.select(singleServer), "RoundRobin should return the only server");
    }

    @RepeatedTest(5)
    @DisplayName("测试 Random 负载均衡器随机性")
    void testRandomnessOfRandomLoadBalancer() {
        LoadBalancer lb = ExtensionLoader.getExtensionLoader(LoadBalancer.class).getExtension("random");

        Set<String> selections = new HashSet<>();
        // 多次选择，应该最终选中所有服务器
        for (int i = 0; i < 100; i++) {
            selections.add(lb.select(servers));
        }

        // 在100次随机选择后，应该覆盖所有3个服务器
        assertEquals(3, selections.size(), "Random load balancer should eventually select all servers");
    }
}
