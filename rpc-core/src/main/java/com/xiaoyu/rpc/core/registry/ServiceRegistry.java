package com.xiaoyu.rpc.core.registry;

import com.xiaoyu.rpc.common.extension.SPI;

import java.net.InetSocketAddress;

/**
 * 服务注册接口
 */
@SPI
public interface ServiceRegistry {
    /**
     * 注册服务
     *
     * @param serviceName       服务名称
     * @param inetSocketAddress 服务地址
     */
    void registerService(String serviceName, InetSocketAddress inetSocketAddress);
}
