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

        // 注册 JVM 关闭挂钩 (优雅下线)
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("检测到 JVM 关闭信号，正在执行优雅下线...");
            // 先注销服务，阻止新流量进入
            serviceRegistry.clearRegistry();
            // 再关闭网络层，让存量请求有机会处理完成
            transportServer.stop();
            log.info("优雅下线完成。");
        }));
    }

    public <T> void register(Class<T> interfaceClass, T serviceImpl) {
        String serviceName = interfaceClass.getName();
        // 先做本地注册，便于请求分发时快速定位实现类
        ServiceRepository.registerService(serviceName, serviceImpl);

        // 再注册到注册中心（Nacos / Local）
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
