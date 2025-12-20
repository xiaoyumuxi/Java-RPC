package client;

import VO.RpcRequest;
import VO.RpcResponse;

import com.google.protobuf.ByteString;
import config.RpcConfig;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
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
        // 创建 Handler 实例
        NettyRpcClientHandler clientHandler = new NettyRpcClientHandler();

        EventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap b = new Bootstrap();
            b.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            // 1. 获取协议配置
                            String protocolName = RpcConfig.getInstance().getProtocol();
                            Protocol protocol = ProtocolFactory.getProtocol(protocolName);

                            // 2. 使用协议自动装配
                            // 注意：这里是客户端，所以第二个参数传 false
                            protocol.config(ch.pipeline(), false);

                            // 3. 最后添加你的业务处理器 (clientHandler)
                            ch.pipeline().addLast(clientHandler);
                        }
                    });

            // 连接服务端(从配置读取地址和端口)
            RpcConfig config = RpcConfig.getInstance();
            ChannelFuture future = b.connect(config.getServerHost(), config.getServerPort()).sync();

            // 准备一个 Future 来接收结果
            CompletableFuture<Object> resultFuture = new CompletableFuture<>();
            clientHandler.setFuture(resultFuture);

            // 发送数据
            future.channel().writeAndFlush(request);

            // --- 步骤C：阻塞等待结果 ---
            // 此时，Handler 的 channelRead0 会被触发，并调用 resultFuture.complete(response)
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