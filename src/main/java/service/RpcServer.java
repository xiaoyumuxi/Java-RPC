package service;


import config.RpcConfig;
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

@Slf4j
@Getter
@Setter
public class RpcServer {
    public static void main(String[] args) throws InterruptedException {
        // 加载配置
        RpcConfig config = RpcConfig.getInstance();
        int serverPort = config.getServerPort();
        
        // 0. 注册服务实现
        NettyRpcHandler.registerService(HelloService.class.getName(), new HelloService() {
            @Override
            public String sayHello(String name) {
                return "Hello, " + name + "! (from Netty Server)";
            }
        });

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
                            protocol.config(ch.pipeline(), true);

                            // 3. 最后添加你的业务处理器 (RpcServerHandler)
                            ch.pipeline().addLast(new NettyRpcHandler());
                        }
                    });

            b.bind(serverPort).sync().channel().closeFuture().sync();
            log.info("RPC Server started on port {}...",serverPort);
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}