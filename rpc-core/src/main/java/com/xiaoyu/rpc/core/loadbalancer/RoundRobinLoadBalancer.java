package com.xiaoyu.rpc.core.loadbalancer;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class RoundRobinLoadBalancer implements LoadBalancer {

    private final AtomicInteger index = new AtomicInteger(0);

    @Override
    public String select(List<String> serviceAddresses) {
        if (serviceAddresses == null || serviceAddresses.isEmpty()) {
            return null;
        }
        // 使用原子操作保证线程安全，& Integer.MAX_VALUE 避免负数（Math.abs(Integer.MIN_VALUE) 仍为负数）
        int currentIndex = index.getAndIncrement() & Integer.MAX_VALUE;
        return serviceAddresses.get(currentIndex % serviceAddresses.size());
    }
}
