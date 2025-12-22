package client;

import Serialization.Serializer;
import Serialization.SerializerCode;
import VO.RpcRequest;
import VO.RpcResponse;

import com.google.protobuf.ByteString;
import config.RpcConfig;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http2.Http2StreamChannel;
import io.netty.handler.codec.http2.Http2StreamChannelBootstrap;
import io.netty.handler.codec.http2.Http2StreamFrameToHttpObjectCodec;
import protocol.Http.HttpRpcDecoder;
import protocol.Http.HttpRpcEncoder;
import protocol.Protocol;
import protocol.ProtocolFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.CompletableFuture;

public class RpcClientProxy {

    // 1. 创建动态代理对象
    @SuppressWarnings("unchecked")
    public static <T> T create(Class<T> clazz) {
        return (T) Proxy.newProxyInstance(
                clazz.getClassLoader(),
                new Class[]{clazz},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        // --- 步骤A：构建 Protobuf 请求对象 ---
                        RpcRequest.Builder builder = RpcRequest.newBuilder()
                                .setInterfaceName(method.getDeclaringClass().getName())
                                .setMethodName(method.getName());

                        // 处理参数类型
                        Class<?>[] parameterTypes = method.getParameterTypes();
                        if (parameterTypes != null) {
                            for (Class<?> paramType : parameterTypes) {
                                builder.addParamTypes(paramType.getName());
                            }
                        }

                        // 处理参数值 (序列化为字节)
                        if (args != null) {
                            for (Object arg : args) {
                                byte[] bytes = objectToBytes(arg);
                                builder.addParameters(ByteString.copyFrom(bytes));
                            }
                        }

                        RpcRequest request = builder.build();

                        // --- 步骤B：发送请求并等待结果 ---
                        return sendRequest(request);
                    }
                }
        );
    }

    // 2. 发送网络请求的核心逻辑
    private static Object sendRequest(RpcRequest request) throws Exception {
        String protocolName = RpcConfig.getInstance().getProtocol();
        boolean isHttp2 = "http2".equalsIgnoreCase(protocolName);
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
                            protocol.config(ch.pipeline(), false); //

                            // 非 H2 模式，直接把业务 Handler 挂在主链上
                            if (!isHttp2) {
                                ch.pipeline().addLast(clientHandler);
                            }
                            // 如果是 HTTP/2，主 Pipeline 只负责基础帧处理，不添加 clientHandler
                        }
                    });

            RpcConfig config = RpcConfig.getInstance();
            ChannelFuture future = b.connect(config.getServerHost(), config.getServerPort()).sync();//等待连接完成

            Channel channel = future.channel();

            CompletableFuture<Object> resultFuture = new CompletableFuture<>();
            clientHandler.setFuture(resultFuture);

            if (isHttp2) {
                // --- HTTP/2 流处理 ---
                Http2StreamChannelBootstrap streamBootstrap = new Http2StreamChannelBootstrap(future.channel());//等待 H2 握手和设置交换完成
                //在简单的 h2c 中，虽然没有 TLS 握手，但有 SETTINGS 帧交换
                Http2StreamChannel streamChannel = streamBootstrap.open().get();//打开流，进行同步初始化

                Serializer serializer = SerializerCode.getSerializerByCode(config.getSerializerCode());

                // 在流通道中构建完整的处理链
                streamChannel.pipeline().addLast(new Http2StreamFrameToHttpObjectCodec(false));
                streamChannel.pipeline().addLast(new HttpRpcEncoder(serializer));
                streamChannel.pipeline().addLast(new HttpRpcDecoder(serializer, RpcResponse.class));
                streamChannel.pipeline().addLast(clientHandler); // 此时 clientHandler 只被添加到了这里

                streamChannel.writeAndFlush(request);
            } else {
                // --- Netty / HTTP 1.1 处理 ---
                future.channel().writeAndFlush(request);
            }

            Object result = resultFuture.get();

            System.out.println("DEBUG: 收到的 result 实际类型是: " + (result == null ? "NULL" : result.getClass().getName()));

            // --- 步骤D：解包与反序列化 ---
            if (result instanceof RpcResponse) {
                RpcResponse rpcResponse = (RpcResponse) result;

                // 这里可以判断一下 rpcResponse.getMessage() 是否是 "Success"
                if (!"Success".equals(rpcResponse.getMessage())) {
                    throw new RuntimeException("服务端报错: " + rpcResponse.getMessage());
                }

                // 获取 Data (ByteString) -> byte[]
                byte[] data = rpcResponse.getData().toByteArray();

                // 反序列化为 Java 对象 (User, String, etc.)
                return bytesToObject(data);
            } else {
                throw new RuntimeException("服务端返回的不是 RpcResponse 类型");
            }

        } finally {
            group.shutdownGracefully();
        }
    }

    // --- 辅助方法：Java 对象 -> byte[] ---
    private static byte[] objectToBytes(Object obj) {
        try (java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
             java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(bos)) {
            oos.writeObject(obj);
            oos.flush();
            return bos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("参数序列化失败", e);
        }
    }

    // --- 辅助方法：byte[] -> Java 对象 ---
    private static Object bytesToObject(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        try (java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(bytes);
             java.io.ObjectInputStream ois = new java.io.ObjectInputStream(bis)) {
            return ois.readObject();
        } catch (Exception e) {
            throw new RuntimeException("结果反序列化失败", e);
        }
    }
}