package com.xiaoyu.rpc.core.transport.netty;

import com.xiaoyu.rpc.common.vo.RpcRequest;
import com.xiaoyu.rpc.common.vo.RpcStatusCode;
import com.xiaoyu.rpc.core.client.ChannelProvider;
import com.xiaoyu.rpc.core.client.NettyRpcClientHandler;
import com.xiaoyu.rpc.core.config.RpcConfig;
import com.xiaoyu.rpc.core.exception.RpcException;
import com.xiaoyu.rpc.core.protocol.Protocol;
import com.xiaoyu.rpc.core.protocol.ProtocolFactory;
import com.xiaoyu.rpc.core.transport.TransportClient;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class NettyTransportClient implements TransportClient {

    private final EventLoopGroup eventLoopGroup;
    private final Bootstrap bootstrap;
    private final ChannelProvider channelProvider;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public NettyTransportClient() {
        RpcConfig config = RpcConfig.getInstance();
        this.eventLoopGroup = new NioEventLoopGroup(
                0,
                new DefaultThreadFactory("rpc-client-io", true));
        this.channelProvider = new ChannelProvider(config.getMaxConnections());
        this.bootstrap = new Bootstrap();
        this.bootstrap.group(eventLoopGroup)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        String protocolName = RpcConfig.getInstance().getProtocol();
                        Protocol protocol = ProtocolFactory.getProtocol(protocolName);
                        protocol.config(ch.pipeline(), false, null);
                    }
                });
    }

    @Override
    public CompletableFuture<Object> sendRequest(RpcRequest request, InetSocketAddress address) {
        if (closed.get()) {
            return CompletableFuture.failedFuture(
                    new RpcException(RpcStatusCode.CLIENT_CLOSED, "NettyTransportClient 已关闭"));
        }

        RpcConfig config = RpcConfig.getInstance();
        String protocolName = config.getProtocol();

        try {
            return channelProvider.get(address, bootstrap)
                    .thenCompose(channel -> sendOnChannel(request, channel, config, protocolName))
                    .whenComplete((result, throwable) -> {
                        if (throwable != null) {
                            log.warn("RPC请求失败: address={}", address, throwable);
                        }
                    });
        } catch (Exception e) {
            log.error("RPC请求发起失败", e);
            return CompletableFuture.failedFuture(
                    new RpcException(RpcStatusCode.UNAVAILABLE, "RPC请求发起失败: " + address, e));
        }
    }

    private CompletableFuture<Object> sendOnChannel(RpcRequest request, Channel channel,
            RpcConfig config, String protocolName) {
        if (closed.get()) {
            return CompletableFuture.failedFuture(
                    new RpcException(RpcStatusCode.CLIENT_CLOSED, "NettyTransportClient 已关闭"));
        }
        if (channel == null || !channel.isActive()) {
            return CompletableFuture.failedFuture(
                    new RpcException(RpcStatusCode.UNAVAILABLE, "无法连接到服务器: " + channel));
        }

        NettyRpcClientHandler handler = channel.pipeline().get(NettyRpcClientHandler.class);
        if (handler == null) {
            synchronized (channel) {
                handler = channel.pipeline().get(NettyRpcClientHandler.class);
                if (handler == null) {
                    handler = new NettyRpcClientHandler();
                    channel.pipeline().addLast(handler);
                }
            }
        }
        final NettyRpcClientHandler clientHandler = handler;

        String requestId = request.getRequestId().isEmpty()
                ? UUID.randomUUID().toString()
                : request.getRequestId();
        RpcRequest newRequest = request.getRequestId().isEmpty()
                ? request.toBuilder().setRequestId(requestId).build()
                : request;

        CompletableFuture<Object> resultFuture = new CompletableFuture<>();
        clientHandler.addFuture(requestId, resultFuture);

        int timeoutMillis = Math.max(1, config.getRequestTimeoutMillis());
        final ScheduledFuture<?> timeoutTask;
        try {
            timeoutTask = channel.eventLoop().schedule(
                    () -> clientHandler.failRequest(requestId,
                            new RpcException(
                                    RpcStatusCode.TIMEOUT,
                                    "RPC请求超时: requestId=" + requestId + ", timeoutMs=" + timeoutMillis)),
                    timeoutMillis,
                    TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            clientHandler.failRequest(requestId, e);
            return CompletableFuture.failedFuture(e);
        }

        resultFuture.whenComplete((result, throwable) -> {
            timeoutTask.cancel(false);
            clientHandler.removeFuture(requestId);
        });

        Protocol protocol = ProtocolFactory.getProtocol(protocolName);
        try {
            protocol.sendRequest(channel, newRequest, clientHandler);
        } catch (Exception e) {
            clientHandler.failRequest(requestId,
                    new RpcException(RpcStatusCode.UNAVAILABLE, "发送 RPC 请求失败", e));
        }

        return resultFuture;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        channelProvider.close();
        Future<?> shutdownFuture = eventLoopGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS);
        if (!isInEventLoop()) {
            shutdownFuture.syncUninterruptibly();
        }
    }

    private boolean isInEventLoop() {
        for (EventExecutor executor : eventLoopGroup) {
            if (executor.inEventLoop()) {
                return true;
            }
        }
        return false;
    }
}
