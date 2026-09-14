package com.xiaoyu.rpc.core.transport.netty;

import com.xiaoyu.rpc.common.vo.RpcRequest;
import com.xiaoyu.rpc.common.vo.RpcResponse;
import com.xiaoyu.rpc.core.client.ChannelProvider;
import com.xiaoyu.rpc.core.client.NettyRpcClientHandler;
import com.xiaoyu.rpc.core.config.RpcConfig;
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
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
public class NettyTransportClient implements TransportClient {

    private static volatile EventLoopGroup eventLoopGroup;
    private static volatile Bootstrap bootstrap;

    private static Bootstrap getBootstrap() {
        if (bootstrap == null) {
            synchronized (NettyTransportClient.class) {
                if (bootstrap == null) {
                    // Bootstrap 和 EventLoopGroup 进程内复用，避免每次请求都创建线程池
                    eventLoopGroup = new NioEventLoopGroup();
                    Bootstrap newBootstrap = new Bootstrap();
                    newBootstrap.group(eventLoopGroup)
                            .channel(NioSocketChannel.class)
                            .handler(new ChannelInitializer<SocketChannel>() {
                                @Override
                                protected void initChannel(SocketChannel ch) {
                                    String protocolName = RpcConfig.getInstance().getProtocol();
                                    Protocol protocol = ProtocolFactory.getProtocol(protocolName);
                                    protocol.config(ch.pipeline(), false, null);
                                }
                            });
                    bootstrap = newBootstrap;
                }
            }
        }
        return bootstrap;
    }

    @Override
    public CompletableFuture<Object> sendRequest(RpcRequest request, InetSocketAddress address) {
        RpcConfig config = RpcConfig.getInstance();
        String protocolName = config.getProtocol();

        try {
            Channel channel = ChannelProvider.get(address, getBootstrap());
            if (channel == null || !channel.isActive()) {
                throw new RuntimeException("无法连接到服务器: " + address);
            }

            NettyRpcClientHandler handler = channel.pipeline().get(NettyRpcClientHandler.class);
            if (handler == null) {
                handler = new NettyRpcClientHandler();
                channel.pipeline().addLast(handler);
            }
            final NettyRpcClientHandler clientHandler = handler;

            String requestId = UUID.randomUUID().toString();
            RpcRequest newRequest = request.toBuilder()
                    .setRequestId(requestId)
                    .build();

            CompletableFuture<Object> resultFuture = new CompletableFuture<>();
            // 必须先注册 Future 再发送，避免极端情况下响应先到。
            clientHandler.addFuture(requestId, resultFuture);

            int timeoutMillis = Math.max(1, config.getRequestTimeoutMillis());
            final ScheduledFuture<?> timeoutTask;
            try {
                timeoutTask = channel.eventLoop().schedule(
                        () -> clientHandler.failRequest(requestId,
                                new TimeoutException("RPC请求超时: requestId=" + requestId
                                        + ", timeoutMs=" + timeoutMillis)),
                        timeoutMillis,
                        TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                clientHandler.failRequest(requestId, e);
                throw e;
            }

            // 无论正常完成、超时还是异常，都取消定时任务并确保 pendingRequests 被清理。
            resultFuture.whenComplete((result, throwable) -> {
                timeoutTask.cancel(false);
                clientHandler.removeFuture(requestId);
            });

            Protocol protocol = ProtocolFactory.getProtocol(protocolName);
            try {
                protocol.sendRequest(channel, newRequest, clientHandler);
            } catch (Exception e) {
                clientHandler.failRequest(requestId, e);
                throw e;
            }

            return resultFuture.thenApply(result -> {
                if (result instanceof RpcResponse) {
                    RpcResponse rpcResponse = (RpcResponse) result;
                    if (!"Success".equals(rpcResponse.getMessage())) {
                        throw new RuntimeException("服务端报错: " + rpcResponse.getMessage());
                    }
                    return rpcResponse;
                }
                throw new RuntimeException("服务端返回的不是 RpcResponse 类型");
            });
        } catch (Exception e) {
            log.error("RPC请求发起失败", e);
            CompletableFuture<Object> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }
}
