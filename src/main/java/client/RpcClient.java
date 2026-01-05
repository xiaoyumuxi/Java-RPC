package client;

import VO.RpcRequest;
import VO.RpcResponse;
import config.RpcConfig;
import extension.ExtensionLoader;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
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

    public Object sendRequest(RpcRequest request) {
        String protocolName = RpcConfig.getInstance().getProtocol();
        NettyRpcClientHandler clientHandler = new NettyRpcClientHandler();

        EventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap b = new Bootstrap();
            b.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            Protocol protocol = ProtocolFactory.getProtocol(protocolName);
                            protocol.config(ch.pipeline(), false, null);
                        }
                    });

            ServiceDiscovery serviceDiscovery = ExtensionLoader.getExtensionLoader(ServiceDiscovery.class)
                    .getExtension(RpcConfig.getInstance().getRegistryType());
            InetSocketAddress address = serviceDiscovery.lookupService(request.getInterfaceName());

            if (address == null) {
                throw new RuntimeException("未发现服务: " + request.getInterfaceName());
            }

            ChannelFuture future = b.connect(address.getHostName(), address.getPort()).sync(); // 等待连接完成

            Channel channel = future.channel();

            CompletableFuture<Object> resultFuture = new CompletableFuture<>();
            clientHandler.setFuture(resultFuture);

            Protocol protocol = ProtocolFactory.getProtocol(protocolName);
            protocol.sendRequest(channel, request, clientHandler);

            Object result = resultFuture.get(5, TimeUnit.SECONDS); // 添加超时时间

            // --- 步骤D：解包与反序列化 ---
            if (result instanceof RpcResponse) {
                RpcResponse rpcResponse = (RpcResponse) result;

                if (!"Success".equals(rpcResponse.getMessage())) {
                    throw new RuntimeException("服务端报错: " + rpcResponse.getMessage());
                }

                byte[] data = rpcResponse.getData().toByteArray();

                return bytesToObject(data);
            } else {
                throw new RuntimeException("服务端返回的不是 RpcResponse 类型");
            }

        } catch (Exception e) {
            throw new RuntimeException("RPC请求发送失败", e);
        } finally {
            group.shutdownGracefully();
        }
    }

    private Object bytesToObject(byte[] bytes) {
        if (bytes == null || bytes.length == 0)
            return null;
        try (java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(bytes);
                java.io.ObjectInputStream ois = new java.io.ObjectInputStream(bis)) {
            return ois.readObject();
        } catch (Exception e) {
            throw new RuntimeException("结果反序列化失败", e);
        }
    }
}
