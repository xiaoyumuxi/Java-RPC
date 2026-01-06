package com.xiaoyu.rpc.core.registry.nacos;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import lombok.extern.slf4j.Slf4j;
import com.xiaoyu.rpc.core.config.RpcConfig;

import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NacosUtils {
    private static final Logger log = LoggerFactory.getLogger(NacosUtils.class);

    private static final NamingService namingService;
    private static final Set<String> serviceNames = new HashSet<>();
    private static InetSocketAddress address;

    static {
        namingService = getNacosNamingService();
    }

    public static NamingService getNacosNamingService() {
        try {
            // 从配置中获取 Nacos 地址，暂时先硬编码或者后续从 RpcConfig 获取
            // 这里我们先假定 RpcConfig 会提供 registryAddress，如果没提供就默认
            String registryAddress = RpcConfig.getInstance().getRegistryAddress();
            if (registryAddress == null || registryAddress.isEmpty()) {
                registryAddress = "127.0.0.1:8848";
            }
            return NamingFactory.createNamingService(registryAddress);
        } catch (NacosException e) {
            log.error("连接 Nacos 时发生错误:", e);
            throw new RuntimeException("连接 Nacos 失败", e);
        }
    }

    public static void registerService(String serviceName, InetSocketAddress address) throws NacosException {
        namingService.registerInstance(serviceName, address.getHostName(), address.getPort());
        serviceNames.add(serviceName);
        NacosUtils.address = address;
    }

    public static List<Instance> getAllInstance(String serviceName) throws NacosException {
        return namingService.getAllInstances(serviceName);
    }

    // 注销所有服务
    public static void clearRegistry() {
        if (!serviceNames.isEmpty() && address != null) {
            String host = address.getHostName();
            int port = address.getPort();
            Iterator<String> iterator = serviceNames.iterator();
            while (iterator.hasNext()) {
                String serviceName = iterator.next();
                try {
                    namingService.deregisterInstance(serviceName, host, port);
                    log.info("注销服务 {} 成功", serviceName);
                } catch (NacosException e) {
                    log.error("注销服务 {} 失败", serviceName, e);
                }
            }
        }
    }
}
