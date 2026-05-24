package com.xiaoyu.rpc.core.transport.netty;

import com.xiaoyu.rpc.core.config.RpcConfig;
import com.xiaoyu.rpc.core.protocol.Protocol;
import com.xiaoyu.rpc.core.protocol.ProtocolDetectHandler;
import com.xiaoyu.rpc.core.protocol.ProtocolFactory;
import com.xiaoyu.rpc.core.server.NettyRpcHandler;
import com.xiaoyu.rpc.core.transport.TransportServer;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NettyTransportServer implements TransportServer {

    private final int port;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    public NettyTransportServer(int port) {
        this.port = port;
    }

    @Override
    public void start() throws InterruptedException {
        // boss 负责接收连接，worker 负责连接上的读写事件
        bossGroup = new NioEventLoopGroup();
        workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            String protocolName = RpcConfig.getInstance().getProtocol();
                            if ("auto".equalsIgnoreCase(protocolName)) {
                                // 自动嗅探模式：先放嗅探器，连接建立后根据首字节判断协议
                                ch.pipeline().addLast(new ProtocolDetectHandler());
                            } else {
                                // 指定协议模式：保持原有行为
                                Protocol protocol = ProtocolFactory.getProtocol(protocolName);
                                protocol.config(ch.pipeline(), true, new NettyRpcHandler());
                            }
                        }
                    });

            log.info("RPC Server (Netty) started on port {}...", port);
            b.bind(port).sync().channel().closeFuture().sync();
        } finally {
            stop();
        }
    }

    @Override
    public void stop() {
        // shutdownGracefully 会等待队列任务处理后再退出，避免直接中断 I/O
        if (bossGroup != null)
            bossGroup.shutdownGracefully();
        if (workerGroup != null)
            workerGroup.shutdownGracefully();
    }
}
