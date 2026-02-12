package com.xiaoyu.rpc.core.transport.netty;

import com.xiaoyu.rpc.common.serialization.Serializer;
import com.xiaoyu.rpc.common.serialization.SerializerCode;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

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
        String protocolName = RpcConfig.getInstance().getProtocol();

        try {
            // 使用 ChannelProvider 获取连接
            Channel channel = ChannelProvider.get(address, getBootstrap());
            if (channel == null || !channel.isActive()) {
                throw new RuntimeException("无法连接到服务器: " + address);
            }

            // Reuse handler from pipeline
            NettyRpcClientHandler clientHandler = channel.pipeline().get(NettyRpcClientHandler.class);
            if (clientHandler == null) {
                // Should be added by initChannel, but for safety in some custom protocols:
                clientHandler = new NettyRpcClientHandler();
                channel.pipeline().addLast(clientHandler);
            }

            // Generate ID and set to request
            // requestId 是客户端关联响应的关键键值，必须在发送前写入
            String requestId = java.util.UUID.randomUUID().toString();
            RpcRequest.Builder builder = request.toBuilder();
            builder.setRequestId(requestId);
            RpcRequest newRequest = builder.build();

            CompletableFuture<Object> resultFuture = new CompletableFuture<>();
            // 先注册 future 再发送，避免极端情况下响应先到导致找不到回调
            clientHandler.addFuture(requestId, resultFuture);

            Protocol protocol = ProtocolFactory.getProtocol(protocolName);
            protocol.sendRequest(channel, newRequest, clientHandler);

            // 彻底移除 resultFuture.get()，直接返回异步 Future
            return resultFuture.thenApply(result -> {
                if (result instanceof RpcResponse) {
                    RpcResponse rpcResponse = (RpcResponse) result;
                    if (!"Success".equals(rpcResponse.getMessage())) {
                        throw new RuntimeException("服务端报错: " + rpcResponse.getMessage());
                    }
                    return rpcResponse;
                } else {
                    throw new RuntimeException("服务端返回的不是 RpcResponse 类型");
                }
            });
        } catch (Exception e) {
            log.error("RPC请求发起失败", e);
            CompletableFuture<Object> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }
}
