package com.xiaoyu.rpc.core.registry.nacos;

import com.alibaba.nacos.api.exception.NacosException;
import lombok.extern.slf4j.Slf4j;
import com.xiaoyu.rpc.core.registry.ServiceRegistry;

import java.net.InetSocketAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NacosServiceRegistry implements ServiceRegistry {
    private static final Logger log = LoggerFactory.getLogger(NacosServiceRegistry.class);

    @Override
    public void registerService(String serviceName, InetSocketAddress inetSocketAddress) {
        try {
            NacosUtils.registerService(serviceName, inetSocketAddress);
            log.info("服务注册成功: {} -> {}", serviceName, inetSocketAddress);
        } catch (NacosException e) {
            log.error("注册服务失败:", e);
            throw new RuntimeException("注册服务失败", e);
        }
    }
}
