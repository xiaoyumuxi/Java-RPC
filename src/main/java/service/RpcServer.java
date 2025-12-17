package service;

import Serialization.MyRpcDecoder;
import Serialization.MyRpcEncoder;
import Serialization.SerializerCode;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;


import static Serialization.SerializerCode.JAVA_SERIALIZER;


public class RpcServer {
    public static void main(String[] args) throws InterruptedException {
        // 0. 注册服务实现
        RpcServerHandler.registerService(HelloService.class.getName(), new HelloService() {
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
                            // 添加自定义的编解码器
                            ch.pipeline().addLast(new MyRpcDecoder());
                            ch.pipeline().addLast(new MyRpcEncoder(SerializerCode.getSerializerByCode(JAVA_SERIALIZER.getCode())));
                            ch.pipeline().addLast(new RpcServerHandler());
                        }
                    });

            System.out.println("RPC Server started on port 8080...");
            b.bind(8080).sync().channel().closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
