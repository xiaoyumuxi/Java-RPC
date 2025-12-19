package service;

import Serialization.MyRpcDecoder;
import Serialization.MyRpcEncoder;
import Serialization.Serializer;
import Serialization.SerializerCode;
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

@Slf4j
@Getter
@Setter
public class RpcServer {
    public static void main(String[] args) throws InterruptedException {
        // 加载配置
        RpcConfig config = RpcConfig.getInstance();
        int portNum = config.getServerPort();
        
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
                            // 从配置文件读取序列化方式
                            RpcConfig config = RpcConfig.getInstance();
                            byte serializerCode = config.getSerializerCode();
                            Serializer serializer = SerializerCode.getSerializerByCode(serializerCode);
                            
                            // 【修改点】服务端解码器，指定解析为 RpcRequest
                            ch.pipeline().addLast(new MyRpcDecoder(VO.RpcRequest.class));
                            ch.pipeline().addLast(new MyRpcEncoder(serializer));
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
