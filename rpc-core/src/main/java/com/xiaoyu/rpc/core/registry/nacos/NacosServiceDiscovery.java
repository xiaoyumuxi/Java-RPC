package com.xiaoyu.rpc.core.registry.nacos;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.Event;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.xiaoyu.rpc.common.extension.ExtensionLoader;
import com.xiaoyu.rpc.core.config.RpcConfig;
import com.xiaoyu.rpc.core.loadbalancer.LoadBalancer;
import com.xiaoyu.rpc.core.registry.ServiceDiscovery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class NacosServiceDiscovery implements ServiceDiscovery {
    private static final Logger log = LoggerFactory.getLogger(NacosServiceDiscovery.class);

    private final NamingService namingService;
    private final LoadBalancer loadBalancer;
    private final Map<String, List<Instance>> serviceCache = new ConcurrentHashMap<>();
    private final Map<String, EventListener> subscriptions = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public NacosServiceDiscovery() {
        this(NacosUtils.getNacosNamingService(),
                ExtensionLoader.getExtensionLoader(LoadBalancer.class)
                        .getExtension(RpcConfig.getInstance().getLoadBalancer()));
    }

    NacosServiceDiscovery(NamingService namingService, LoadBalancer loadBalancer) {
        this.namingService = namingService;
        this.loadBalancer = loadBalancer;
    }

    @Override
    public InetSocketAddress lookupService(String serviceName) {
        if (closed.get()) {
            throw new IllegalStateException("NacosServiceDiscovery 已关闭");
        }

        try {
            ensureSubscribed(serviceName);

            List<Instance> instances = namingService.getAllInstances(serviceName);
            if (instances == null || instances.isEmpty()) {
                // 注册中心明确返回空实例，代表当前服务已下线；不能继续使用旧缓存。
                serviceCache.remove(serviceName);
                throw new RuntimeException("未找到服务: " + serviceName);
            }

            updateCache(serviceName, instances);
            return selectAddress(instances);
        } catch (NacosException e) {
            // 只有 Nacos 网络/协议异常时，才允许使用最后一次成功结果容错。
            log.error("获取服务实例时发生 Nacos 异常，尝试使用最近一次成功缓存: {}", serviceName, e);
            List<Instance> cachedInstances = serviceCache.get(serviceName);
            if (cachedInstances != null && !cachedInstances.isEmpty()) {
                return selectAddress(cachedInstances);
            }
            throw new RuntimeException("服务发现失败且无缓存可用: " + serviceName, e);
        }
    }

    private void ensureSubscribed(String serviceName) throws NacosException {
        if (subscriptions.containsKey(serviceName)) {
            return;
        }

        synchronized (subscriptions) {
            if (subscriptions.containsKey(serviceName)) {
                return;
            }

            EventListener listener = new EventListener() {
                @Override
                public void onEvent(Event event) {
                    if (event instanceof NamingEvent) {
                        List<Instance> instances = ((NamingEvent) event).getInstances();
                        updateCache(serviceName, instances);
                        log.info("监听到服务变更，更新本地缓存: {} -> 实例数 {}",
                                serviceName, instances == null ? 0 : instances.size());
                    }
                }
            };
            namingService.subscribe(serviceName, listener);
            subscriptions.put(serviceName, listener);
        }
    }

    void updateCache(String serviceName, List<Instance> instances) {
        if (instances == null || instances.isEmpty()) {
            serviceCache.remove(serviceName);
        } else {
            serviceCache.put(serviceName, List.copyOf(instances));
        }
    }

    private InetSocketAddress selectAddress(List<Instance> instances) {
        List<String> addressList = instances.stream()
                .map(instance -> instance.getIp() + ":" + instance.getPort())
                .toList();
        String targetAddress = loadBalancer.select(addressList);
        String[] array = targetAddress.split(":");
        return new InetSocketAddress(array[0], Integer.parseInt(array[1]));
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        subscriptions.forEach((serviceName, listener) -> {
            try {
                namingService.unsubscribe(serviceName, listener);
            } catch (NacosException e) {
                log.warn("取消 Nacos 服务订阅失败: {}", serviceName, e);
            }
        });
        subscriptions.clear();
        serviceCache.clear();

        try {
            namingService.shutDown();
        } catch (NacosException e) {
            log.warn("关闭 Nacos NamingService 失败", e);
        }
    }
}
