package com.xiaoyu.rpc.core.server;

import com.xiaoyu.rpc.core.config.RpcConfig;
import com.xiaoyu.rpc.common.extension.ExtensionLoader;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.extern.slf4j.Slf4j;
import com.xiaoyu.rpc.core.protocol.Protocol;
import com.xiaoyu.rpc.core.protocol.ProtocolFactory;
import com.xiaoyu.rpc.core.registry.ServiceRegistry;
import com.xiaoyu.rpc.common.vo.RpcRequest;

import java.net.InetSocketAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RpcServer {
    private static final Logger log = LoggerFactory.getLogger(RpcServer.class);

    private final String serverHost;
    private final int serverPort;
    private final String protocolName;
    private final ServiceRegistry serviceRegistry;

    public RpcServer() {
        RpcConfig config = RpcConfig.getInstance();
        this.serverHost = config.getServerHost();
        this.serverPort = config.getServerPort();
        this.protocolName = config.getProtocol();
        this.serviceRegistry = ExtensionLoader.getExtensionLoader(ServiceRegistry.class)
                .getExtension(config.getRegistryType());
    }

    public <T> void register(Class<T> interfaceClass, T serviceImpl) {
        String serviceName = interfaceClass.getName();
        // 1. 本地注册 (Netty Handler)
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
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            Protocol protocol = ProtocolFactory.getProtocol(protocolName);
                            protocol.config(ch.pipeline(), true, new NettyRpcHandler());
                        }
                    });

            log.info("RPC Server started on port {}...", serverPort);
            b.bind(serverPort).sync().channel().closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

}