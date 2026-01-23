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
        // 1. 本地注册 (仍然使用 NettyRpcHandler 的静态方法? 这里需要注意)
        // 为了兼容现有代码，NettyRpcHandler 仍然作为 Handler，但它的 handlerMap 是静态的
        // 理想情况下应该把 Handler 变成非静态的并传给 TransportServer
        // 但目前 NettyTransportServer 内部硬编码了 new NettyRpcHandler()，而 NettyRpcHandler 使用静态
        // map
        // 所以这里依然有效。后续应该优化 NettyRpcHandler 的状态管理。
        NettyRpcHandler.registerService(serviceName, serviceImpl);

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