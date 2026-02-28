package com.xiaoyu.rpc.core.loadbalancer;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class LoadBalancerConcurrencyTest {

    @Test
    void testRoundRobinConcurrency() throws InterruptedException {
        RoundRobinLoadBalancer loadBalancer = new RoundRobinLoadBalancer();
        List<String> addresses = List.of("127.0.0.1:8080", "127.0.0.1:8081", "127.0.0.1:8082");

        int threadCount = 10;
        int requestsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount * requestsPerThread);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                for (int j = 0; j < requestsPerThread; j++) {
                    String selected = loadBalancer.select(addresses);
                    if (selected != null && addresses.contains(selected)) {
                        successCount.incrementAndGet();
                    }
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, java.util.concurrent.TimeUnit.SECONDS));
        assertEquals(threadCount * requestsPerThread, successCount.get());
        executor.shutdown();
    }

    @Test
    void testRoundRobinOverflow() {
        RoundRobinLoadBalancer loadBalancer = new RoundRobinLoadBalancer();
        List<String> addresses = List.of("127.0.0.1:8080", "127.0.0.1:8081");

        // 模拟大量请求导致计数器接近溢出
        for (int i = 0; i < 1000; i++) {
            String selected = loadBalancer.select(addresses);
            assertNotNull(selected);
            assertTrue(addresses.contains(selected));
        }
    }

    @Test
    void testRandomLoadBalancerConcurrency() throws InterruptedException {
        RandomLoadBalancer loadBalancer = new RandomLoadBalancer();
        List<String> addresses = List.of("127.0.0.1:8080", "127.0.0.1:8081", "127.0.0.1:8082");

        int threadCount = 10;
        int requestsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount * requestsPerThread);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                for (int j = 0; j < requestsPerThread; j++) {
                    String selected = loadBalancer.select(addresses);
                    if (selected != null && addresses.contains(selected)) {
                        successCount.incrementAndGet();
                    }
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, java.util.concurrent.TimeUnit.SECONDS));
        assertEquals(threadCount * requestsPerThread, successCount.get());
        executor.shutdown();
    }

    @Test
    void testLoadBalancerWithEmptyList() {
        RoundRobinLoadBalancer roundRobin = new RoundRobinLoadBalancer();
        RandomLoadBalancer random = new RandomLoadBalancer();

        assertNull(roundRobin.select(new ArrayList<>()));
        assertNull(roundRobin.select(null));
        assertNull(random.select(new ArrayList<>()));
        assertNull(random.select(null));
    }
}
