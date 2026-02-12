package com.xiaoyu.rpc.core.registry.nacos;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.listener.Event;
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.xiaoyu.rpc.common.extension.ExtensionLoader;
import com.xiaoyu.rpc.core.config.RpcConfig;
import com.xiaoyu.rpc.core.loadbalancer.LoadBalancer;
import com.xiaoyu.rpc.core.registry.ServiceDiscovery;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class NacosServiceDiscovery implements ServiceDiscovery {
    private static final Logger log = LoggerFactory.getLogger(NacosServiceDiscovery.class);

    private final NamingService namingService;
    private final LoadBalancer loadBalancer;
    // 本地缓存，用于容错和防抖
    private static final java.util.Map<String, List<Instance>> serviceCache = new java.util.concurrent.ConcurrentHashMap<>();
    // 已订阅的服务集合
    private static final java.util.Set<String> subscribedServices = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public NacosServiceDiscovery() {
        this.namingService = NacosUtils.getNacosNamingService();
        String loadBalancerCode = RpcConfig.getInstance().getLoadBalancer();
        this.loadBalancer = ExtensionLoader.getExtensionLoader(LoadBalancer.class).getExtension(loadBalancerCode);
    }

    @Override
    public InetSocketAddress lookupService(String serviceName) {
        try {
            // 第一次查找时订阅服务变更
            if (subscribedServices.add(serviceName)) {
                // add 返回 true 说明此前未订阅，避免同一个服务被重复订阅
                subscribeService(serviceName);
            }

            // 优先从 Nacos 拉取最新实例列表
            List<Instance> instances = namingService.getAllInstances(serviceName);

            if (instances.isEmpty()) {
                log.warn("Nacos 返回实例列表为空，尝试使用本地缓存: {}", serviceName);
                instances = serviceCache.get(serviceName);
            } else {
                // 更新本地缓存
                serviceCache.put(serviceName, instances);
            }

            if (instances == null || instances.isEmpty()) {
                log.error("未找到服务且本地无缓存: {}", serviceName);
                throw new RuntimeException("未找到服务: " + serviceName);
            }

            // 转换 Instance 列表为 String 列表 (ip:port)
            List<String> addressList = instances.stream()
                    .map(instance -> instance.getIp() + ":" + instance.getPort())
                    .collect(java.util.stream.Collectors.toList());

            // 负载均衡选择
            String targetAddress = loadBalancer.select(addressList);
            log.info("负载均衡选择服务地址: {}", targetAddress);

            String[] array = targetAddress.split(":");
            return new InetSocketAddress(array[0], Integer.parseInt(array[1]));

        } catch (NacosException e) {
            log.error("获取服务实例时发生网络异常，尝试回滚到本地缓存:", e);
            // Nacos 短暂不可用时，优先用最近一次成功拉取到的实例兜底
            List<Instance> cachedInstances = serviceCache.get(serviceName);
            if (cachedInstances != null && !cachedInstances.isEmpty()) {
                List<String> addressList = cachedInstances.stream()
                        .map(instance -> instance.getIp() + ":" + instance.getPort())
                        .collect(java.util.stream.Collectors.toList());
                String targetAddress = loadBalancer.select(addressList);
                String[] array = targetAddress.split(":");
                return new InetSocketAddress(array[0], Integer.parseInt(array[1]));
            }
            throw new RuntimeException("服务发现失败且无缓存可用: " + serviceName, e);
        }
    }

    /**
     * 订阅服务变更，实现本地缓存的实时更新
     */
    private void subscribeService(String serviceName) throws NacosException {
        namingService.subscribe(serviceName, new EventListener() {
            @Override
            public void onEvent(Event event) {
                if (event instanceof NamingEvent) {
                    NamingEvent namingEvent = (NamingEvent) event;
                    List<Instance> instances = namingEvent.getInstances();
                    log.info("监听到服务变更，更新本地缓存: {} -> 实例数 {}", serviceName, instances.size());
                    if (instances != null && !instances.isEmpty()) {
                        serviceCache.put(serviceName, instances);
                    }
                }
            }
        });
    }
}
