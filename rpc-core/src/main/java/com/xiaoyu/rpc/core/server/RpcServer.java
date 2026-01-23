package com.xiaoyu.rpc.core.server;

import com.xiaoyu.rpc.core.config.RpcConfig;
import com.xiaoyu.rpc.common.extension.ExtensionLoader;
import com.xiaoyu.rpc.core.registry.ServiceRegistry;
import com.xiaoyu.rpc.core.transport.Transport;
import com.xiaoyu.rpc.core.transport.TransportServer;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;

@Slf4j
public class RpcServer {

    private final String serverHost;
    private final int serverPort;
    private final ServiceRegistry serviceRegistry;
    private final TransportServer transportServer;

    public RpcServer() {
        RpcConfig config = RpcConfig.getInstance();
        this.serverHost = config.getServerHost();
        this.serverPort = config.getServerPort();
        this.serviceRegistry = ExtensionLoader.getExtensionLoader(ServiceRegistry.class)
                .getExtension(config.getRegistryType());

        // 获取传输层实现
        Transport transport = ExtensionLoader.getExtensionLoader(Transport.class).getExtension(config.getTransport());
        this.transportServer = transport.createServer(this.serverPort);
    }

    public <T> void register(Class<T> interfaceClass, T serviceImpl) {
        String serviceName = interfaceClass.getName();
        // 1. 本地注册 (使用 ServiceRepository 解耦)
        ServiceRepository.registerService(serviceName, serviceImpl);

        // 2. 远程注册 (Nacos / Local)
        try {
            serviceRegistry.registerService(serviceName, new InetSocketAddress(serverHost, serverPort));
            log.info("Service registered: {}", serviceName);
        } catch (Exception e) {
            log.error("Failed to register service: {}", serviceName, e);
        }
    }

    public void start() throws InterruptedException {
        transportServer.start();
    }
}