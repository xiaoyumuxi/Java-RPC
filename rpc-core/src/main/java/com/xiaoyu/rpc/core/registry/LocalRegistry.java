package com.xiaoyu.rpc.core.registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LocalRegistry implements ServiceRegistry, ServiceDiscovery {

    private static final Logger log = LoggerFactory.getLogger(LocalRegistry.class);
    private static final Map<String, InetSocketAddress> SERVICES = new ConcurrentHashMap<>();

    @Override
    public void registerService(String serviceName, InetSocketAddress inetSocketAddress) {
        SERVICES.put(serviceName, inetSocketAddress);
        log.info("LocalRegistry registered {} at {}", serviceName, inetSocketAddress);
    }

    @Override
    public InetSocketAddress lookupService(String serviceName) {
        log.debug("LocalRegistry lookup: {}", serviceName);
        return SERVICES.get(serviceName);
    }

    @Override
    public void clearRegistry() {
        SERVICES.clear();
        log.info("LocalRegistry cleared all services");
    }
}
