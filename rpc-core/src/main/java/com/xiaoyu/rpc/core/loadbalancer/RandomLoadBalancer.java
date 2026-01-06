package com.xiaoyu.rpc.core.loadbalancer;

import java.util.List;
import java.util.Random;

public class RandomLoadBalancer implements LoadBalancer {

    private final Random random = new Random();

    @Override
    public String select(List<String> serviceAddresses) {
        if (serviceAddresses == null || serviceAddresses.isEmpty()) {
            return null;
        }
        int index = random.nextInt(serviceAddresses.size());
        return serviceAddresses.get(index);
    }
}
