package com.xiaoyu.rpc.core.server;

import com.xiaoyu.rpc.common.extension.ExtensionLoader;
import com.xiaoyu.rpc.core.config.RpcConfig;
import com.xiaoyu.rpc.core.registry.ServiceRegistry;
import com.xiaoyu.rpc.core.transport.Transport;
import com.xiaoyu.rpc.core.transport.TransportServer;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class RpcServer implements AutoCloseable {

    private final String serverHost;
    private final int serverPort;
    private final ServiceRegistry serviceRegistry;
    private final TransportServer transportServer;
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private final Thread shutdownHook;

    public RpcServer() {
        this(RpcConfig.getInstance());
    }

    private RpcServer(RpcConfig config) {
        this(
                config.getServerHost(),
                config.getServerPort(),
                ExtensionLoader.getExtensionLoader(ServiceRegistry.class)
                        .getExtension(config.getRegistryType()),
                ExtensionLoader.getExtensionLoader(Transport.class)
                        .getExtension(config.getTransport())
                        .createServer(config.getServerPort()),
                true);
    }

    RpcServer(String serverHost, int serverPort, ServiceRegistry serviceRegistry,
            TransportServer transportServer) {
        this(serverHost, serverPort, serviceRegistry, transportServer, false);
    }

    private RpcServer(String serverHost, int serverPort, ServiceRegistry serviceRegistry,
            TransportServer transportServer, boolean installShutdownHook) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.serviceRegistry = serviceRegistry;
        this.transportServer = transportServer;

        if (installShutdownHook) {
            this.shutdownHook = new Thread(() -> {
                log.info("检测到 JVM 关闭信号，正在执行优雅下线...");
                stop();
                log.info("优雅下线完成。");
            }, "rpc-server-shutdown");
            Runtime.getRuntime().addShutdownHook(this.shutdownHook);
        } else {
            this.shutdownHook = null;
        }
    }

    public <T> void register(Class<T> interfaceClass, T serviceImpl) {
        if (stopped.get()) {
            throw new IllegalStateException("RpcServer 已关闭");
        }

        String serviceName = interfaceClass.getName();
        ServiceRepository.registerService(serviceName, serviceImpl);

        try {
            serviceRegistry.registerService(serviceName, new InetSocketAddress(serverHost, serverPort));
            log.info("Service registered: {}", serviceName);
        } catch (Exception e) {
            log.error("Failed to register service: {}", serviceName, e);
        }
    }

    public void start() throws InterruptedException {
        if (stopped.get()) {
            throw new IllegalStateException("RpcServer 已关闭");
        }

        try {
            transportServer.start();
        } finally {
            stop();
        }
    }

    public void stop() {
        if (!stopped.compareAndSet(false, true)) {
            return;
        }

        try {
            serviceRegistry.clearRegistry();
        } catch (Exception e) {
            log.warn("清理服务注册信息失败", e);
        }

        try {
            transportServer.stop();
        } catch (Exception e) {
            log.warn("停止 RPC TransportServer 失败", e);
        }

        removeShutdownHook();
    }

    @Override
    public void close() {
        stop();
    }

    private void removeShutdownHook() {
        if (shutdownHook == null || Thread.currentThread() == shutdownHook) {
            return;
        }
        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (IllegalStateException | SecurityException ignored) {
            // JVM 已进入关闭阶段时无法移除 Hook，忽略即可。
        }
    }
}
