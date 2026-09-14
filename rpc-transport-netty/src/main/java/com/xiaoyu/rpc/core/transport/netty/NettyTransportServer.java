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

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class NettyTransportServer implements TransportServer {

    private final int port;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private ThreadPoolExecutor businessExecutor;

    public NettyTransportServer(int port) {
        this.port = port;
    }

    @Override
    public void start() throws InterruptedException {
        RpcConfig config = RpcConfig.getInstance();
        int cpuCores = Runtime.getRuntime().availableProcessors();
        int bossThreads = Math.max(1, config.getBossThreads());
        int workerThreads = config.getWorkerThreads() != null && config.getWorkerThreads() > 0
                ? config.getWorkerThreads()
                : Math.max(1, cpuCores * 2);
        int businessThreads = config.getBusinessThreads() != null && config.getBusinessThreads() > 0
                ? config.getBusinessThreads()
                : Math.max(1, cpuCores);
        int businessQueueCapacity = Math.max(1, config.getBusinessQueueCapacity());

        bossGroup = new NioEventLoopGroup(bossThreads);
        workerGroup = new NioEventLoopGroup(workerThreads);
        businessExecutor = new ThreadPoolExecutor(
                businessThreads,
                businessThreads,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(businessQueueCapacity),
                new NamedThreadFactory("rpc-business-"),
                new ThreadPoolExecutor.AbortPolicy());

        // 一个服务端实例共享同一个无状态 Handler 和业务线程池，避免按连接创建线程资源。
        NettyRpcHandler serverHandler = new NettyRpcHandler(businessExecutor);

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            String protocolName = RpcConfig.getInstance().getProtocol();
                            if ("auto".equalsIgnoreCase(protocolName)) {
                                ch.pipeline().addLast(new ProtocolDetectHandler(serverHandler));
                            } else {
                                Protocol protocol = ProtocolFactory.getProtocol(protocolName);
                                protocol.config(ch.pipeline(), true, serverHandler);
                            }
                        }
                    });

            log.info("RPC Server (Netty) started on port {}, bossThreads={}, workerThreads={}, businessThreads={}, "
                            + "businessQueueCapacity={}",
                    port, bossThreads, workerThreads, businessThreads, businessQueueCapacity);
            bootstrap.bind(port).sync().channel().closeFuture().sync();
        } finally {
            stop();
        }
    }

    @Override
    public void stop() {
        // 先停止接收新连接，并开始关闭 I/O 线程。
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }

        // 不再接收新任务后，尽量等待已提交的业务请求执行完成。
        if (businessExecutor != null) {
            businessExecutor.shutdown();
            try {
                if (!businessExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    businessExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                businessExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private static final class NamedThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger sequence = new AtomicInteger(1);

        private NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, prefix + sequence.getAndIncrement());
            thread.setDaemon(false);
            return thread;
        }
    }
}
