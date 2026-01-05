package client;

import Serialization.Serializer;
import Serialization.SerializerCode;

import VO.RpcRequest;
import VO.RpcResponse;
import config.RpcConfig;
import extension.ExtensionLoader;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import protocol.Protocol;
import protocol.ProtocolFactory;
import registry.ServiceDiscovery;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class RpcClient {

    private static final EventLoopGroup eventLoopGroup;
    private static final Bootstrap bootstrap;

    static {
        eventLoopGroup = new NioEventLoopGroup();
        bootstrap = new Bootstrap();
        bootstrap.group(eventLoopGroup)
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

    public Object sendRequest(RpcRequest request, Class<?> returnType) {
        String protocolName = RpcConfig.getInstance().getProtocol();
        NettyRpcClientHandler clientHandler = new NettyRpcClientHandler();

        try {
            ServiceDiscovery serviceDiscovery = ExtensionLoader.getExtensionLoader(ServiceDiscovery.class)
                    .getExtension(RpcConfig.getInstance().getRegistryType());
            InetSocketAddress address = serviceDiscovery.lookupService(request.getInterfaceName());

            if (address == null) {
                throw new RuntimeException("未发现服务: " + request.getInterfaceName());
            }

            // 使用 ChannelProvider 获取连接
            Channel channel = ChannelProvider.get(address, bootstrap);
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

                byte[] data = rpcResponse.getData().toByteArray();

                Serializer serializer = SerializerCode.getSerializerByCode(RpcConfig.getInstance().getSerializerCode());
                return serializer.deserialize(data, returnType);
            } else {
                throw new RuntimeException("服务端返回的不是 RpcResponse 类型");
            }

        } catch (Exception e) {
            throw new RuntimeException("RPC请求发送失败", e);
        }
    }
}
