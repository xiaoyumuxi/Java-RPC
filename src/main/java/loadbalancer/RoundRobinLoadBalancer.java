package loadbalancer;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class RoundRobinLoadBalancer implements LoadBalancer {

    private final AtomicInteger index = new AtomicInteger(0);

    @Override
    public String select(List<String> serviceAddresses) {
        if (serviceAddresses == null || serviceAddresses.isEmpty()) {
            return null;
        }
        // 使用原子操作保证线程安全，Math.abs避免负数
        int currentIndex = Math.abs(index.getAndIncrement());
        return serviceAddresses.get(currentIndex % serviceAddresses.size());
    }
}
