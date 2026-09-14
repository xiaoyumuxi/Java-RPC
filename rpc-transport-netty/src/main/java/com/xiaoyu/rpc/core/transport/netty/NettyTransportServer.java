package com.xiaoyu.rpc.core.transport.netty;

import com.xiaoyu.rpc.core.config.RpcConfig;
import com.xiaoyu.rpc.core.protocol.Protocol;
import com.xiaoyu.rpc.core.protocol.ProtocolDetectHandler;
import com.xiaoyu.rpc.core.protocol.ProtocolFactory;
import com.xiaoyu.rpc.core.server.NettyRpcHandler;
import com.xiaoyu.rpc.core.transport.TransportServer;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class NettyTransportServer implements TransportServer {

    private final int port;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private volatile Channel serverChannel;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private ThreadPoolExecutor businessExecutor;

    public NettyTransportServer(int port) {
        this.port = port;
    }

    @Override
    public void start() throws InterruptedException {
        if (stopped.get()) {
            throw new IllegalStateException("NettyTransportServer 已关闭");
        }
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("NettyTransportServer 已启动");
        }

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

            serverChannel = bootstrap.bind(port).sync().channel();
            log.info("RPC Server (Netty) ready on port {}, bossThreads={}, workerThreads={}, businessThreads={}, "
                            + "businessQueueCapacity={}",
                    port, bossThreads, workerThreads, businessThreads, businessQueueCapacity);
        } catch (InterruptedException | RuntimeException e) {
            stop();
            throw e;
        }
    }

    @Override
    public void awaitTermination() throws InterruptedException {
        Channel channel = serverChannel;
        if (channel != null) {
            channel.closeFuture().sync();
        }
    }

    @Override
    public void stop() {
        if (!stopped.compareAndSet(false, true)) {
            return;
        }

        Channel channel = serverChannel;
        if (channel != null) {
            channel.close().syncUninterruptibly();
        }

        shutdownEventLoop(workerGroup);
        shutdownEventLoop(bossGroup);

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

    private static void shutdownEventLoop(EventLoopGroup group) {
        if (group == null) {
            return;
        }

        Future<?> shutdownFuture = group.shutdownGracefully(0, 5, TimeUnit.SECONDS);
        boolean calledFromGroup = false;
        for (EventExecutor executor : group) {
            if (executor.inEventLoop()) {
                calledFromGroup = true;
                break;
            }
        }
        if (!calledFromGroup) {
            shutdownFuture.syncUninterruptibly();
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
