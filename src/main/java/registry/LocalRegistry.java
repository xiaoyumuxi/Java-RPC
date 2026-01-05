package registry;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LocalRegistry implements ServiceRegistry, ServiceDiscovery {

    private static final Map<String, InetSocketAddress> SERVICES = new ConcurrentHashMap<>();

    @Override
    public void registerService(String serviceName, InetSocketAddress inetSocketAddress) {
        SERVICES.put(serviceName, inetSocketAddress);
        System.out.println("LocalRegistry: Registered " + serviceName + " at " + inetSocketAddress);
    }

    @Override
    public InetSocketAddress lookupService(String serviceName) {
        System.out.println("LocalRegistry: Looking up " + serviceName);
        return SERVICES.get(serviceName);
    }
}
