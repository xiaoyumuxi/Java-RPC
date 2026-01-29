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
    public Object sendRequest(RpcRequest request, InetSocketAddress address) {
        NettyRpcClientHandler clientHandler = new NettyRpcClientHandler();
        String protocolName = RpcConfig.getInstance().getProtocol();

        try {
            // 使用 ChannelProvider 获取连接
            Channel channel = ChannelProvider.get(address, getBootstrap());
            if (channel == null || !channel.isActive()) {
                throw new RuntimeException("无法连接到服务器: " + address);
            }

            CompletableFuture<Object> resultFuture = new CompletableFuture<>();
            clientHandler.setFuture(resultFuture);

            Protocol protocol = ProtocolFactory.getProtocol(protocolName);
            protocol.sendRequest(channel, request, clientHandler);

            Object result = resultFuture.get(5, TimeUnit.SECONDS);

            if (result instanceof RpcResponse) {
                RpcResponse rpcResponse = (RpcResponse) result;

                if (!"Success".equals(rpcResponse.getMessage())) {
                    throw new RuntimeException("服务端报错: " + rpcResponse.getMessage());
                }

                // 返回 Data 的 bytes，由上层可以通过配置的 Serializer 进行反序列化
                // 这里为了保持兼容性，还是直接返回 Response 对象或者 Data？
                // 根据 RpcClient 的逻辑，它是在这里反序列化的。
                // 此时我们其实只负责传输，最好返回 RpcResponse 对象。
                return rpcResponse;

            } else {
                throw new RuntimeException("服务端返回的不是 RpcResponse 类型");
            }
        } catch (Exception e) {
            throw new RuntimeException("RPC请求发送失败", e);
        }
    }
}
