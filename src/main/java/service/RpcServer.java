package service;

import config.RpcConfig;
import extension.ExtensionLoader;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import protocol.Protocol;
import protocol.ProtocolFactory;
import registry.ServiceRegistry;

import java.net.InetSocketAddress;

@Slf4j
@Getter
@Setter
public class RpcServer {
    public static void main(String[] args) throws InterruptedException {
        // 加载配置
        RpcConfig config = RpcConfig.getInstance();
        int serverPort = config.getServerPort();
        String serverHost = config.getServerHost();

        // 0. 注册服务实现
        String serviceName = HelloService.class.getName();
        NettyRpcHandler.registerService(serviceName, new HelloService() {
            @Override
            public String sayHello(String name) {
                return "Hello, " + name + "! (from Netty Server)";
            }
        });

        // 注册到 Nacos
        try {
            ServiceRegistry serviceRegistry = ExtensionLoader.getExtensionLoader(ServiceRegistry.class)
                    .getExtension("nacos");
            serviceRegistry.registerService(serviceName, new InetSocketAddress(serverHost, serverPort));
        } catch (Exception e) {
            log.error("注册服务到 Nacos 失败", e);
        }

        // Netty 启动模板代码
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            // 1. 获取协议配置
                            String protocolName = RpcConfig.getInstance().getProtocol();
                            Protocol protocol = ProtocolFactory.getProtocol(protocolName);

                            // 2. 使用协议自动装配
                            protocol.config(ch.pipeline(), true, new NettyRpcHandler());
                        }
                    });

            b.bind(serverPort).sync().channel().closeFuture().sync();
            log.info("RPC Server started on port {}...", serverPort);
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}