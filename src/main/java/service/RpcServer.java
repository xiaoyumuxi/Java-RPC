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
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;


import static Serialization.SerializerCode.JAVA_SERIALIZER;
import static Serialization.SerializerCode.Kryo_SERIALIZER;

@Slf4j
@Getter
@Setter
public class RpcServer {
    static int portNum = 8080;

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
                            ch.pipeline().addLast(new MyRpcEncoder(SerializerCode.getSerializerByCode(Kryo_SERIALIZER.getCode())));
                            ch.pipeline().addLast(new RpcServerHandler());
                        }
                    });

            b.bind(portNum).sync().channel().closeFuture().sync();
            log.info("RPC Server started on port {}...",portNum);
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
