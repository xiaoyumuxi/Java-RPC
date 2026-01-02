package protocol.Http2;

import Serialization.Serializer;
import Serialization.SerializerCode;
import VO.RpcRequest;
import VO.RpcResponse;
import config.RpcConfig;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http2.*;
import io.netty.util.ReferenceCountUtil;
import protocol.Http.HttpRpcDecoder;
import protocol.Http.HttpRpcEncoder;
import protocol.Protocol;

public class Http2Protocol implements Protocol {

    @Override
    public String getName() {
        return "http2";
    }

    @Override
    public void config(ChannelPipeline pipeline, boolean isServer, io.netty.channel.ChannelHandler serverHandler) {
        RpcConfig rpcConfig = RpcConfig.getInstance();
        Serializer serializer = SerializerCode.getSerializerByCode(rpcConfig.getSerializerCode());

        if (isServer) {
            Http2FrameCodec frameCodec = Http2FrameCodecBuilder.forServer()
                    .autoAckSettingsFrame(true) // 自动确认设置帧
                    .autoAckPingFrame(true) // 自动确认ping帧
                    .build();

            // 使用新的构造方式，指定子通道的处理器
            Http2MultiplexHandler multiplexHandler = new Http2MultiplexHandler(
                    new ChannelInitializer<Http2StreamChannel>() {
                        @Override
                        protected void initChannel(Http2StreamChannel ch) {
                            // 在子通道（Stream）中注入转换层
                            ch.pipeline().addLast(new Http2StreamFrameToHttpObjectCodec(true));
                            ch.pipeline().addLast(new io.netty.handler.codec.http.HttpObjectAggregator(512 * 1024));
                            ch.pipeline().addLast(new HttpRpcDecoder(serializer, RpcRequest.class));
                            ch.pipeline().addLast(new HttpRpcEncoder(serializer));

                            if (serverHandler != null) {
                                ch.pipeline().addLast(serverHandler);
                            }
                        }
                    });
            pipeline.addLast(frameCodec, multiplexHandler);

        } else {
            // 客户端核心配置
            Http2FrameCodec frameCodec = Http2FrameCodecBuilder.forClient()
                    // 强制自动处理一些基础帧，防止它们掉到 TailContext
                    .autoAckSettingsFrame(true)
                    .autoAckPingFrame(true)
                    .initialSettings(Http2Settings.defaultSettings().maxHeaderListSize(8192))
                    .build();

            pipeline.addLast(frameCodec);
            // MultiplexHandler 必须紧跟其后
            pipeline.addLast(new Http2MultiplexHandler(new ChannelInboundHandlerAdapter() {

                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) {
                    // 如果还有残留的设置帧传到这里，说明 FrameCodec 没拦截住
                    // 打印一下以便调试，或者直接释放
                    ReferenceCountUtil.release(msg);
                }

                @Override
                public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                    // 处理用户事件，如Http2SettingsAckFrame等
                    ctx.fireUserEventTriggered(evt);
                }
            }));
        }
    }

    @Override
    public void sendRequest(io.netty.channel.Channel channel, RpcRequest request,
            client.NettyRpcClientHandler clientHandler) throws Exception {
        RpcConfig rpcConfig = RpcConfig.getInstance();
        Serializer serializer = SerializerCode.getSerializerByCode(rpcConfig.getSerializerCode());

        // 使用 bootstrap 创建新流
        io.netty.handler.codec.http2.Http2StreamChannelBootstrap streamBootstrap = new io.netty.handler.codec.http2.Http2StreamChannelBootstrap(
                channel);
        Http2StreamChannel streamChannel = streamBootstrap.open().get(5, java.util.concurrent.TimeUnit.SECONDS);

        // 在流通道中构建完整的处理链
        streamChannel.pipeline().addLast(new Http2StreamFrameToHttpObjectCodec(false));
        streamChannel.pipeline().addLast(new io.netty.handler.codec.http.HttpObjectAggregator(512 * 1024));
        streamChannel.pipeline().addLast(new HttpRpcEncoder(serializer));
        streamChannel.pipeline().addLast(new HttpRpcDecoder(serializer, RpcResponse.class));
        streamChannel.pipeline().addLast(clientHandler);

        streamChannel.writeAndFlush(request).addListener(future -> {
            if (!future.isSuccess()) {
                clientHandler.getFuture().completeExceptionally(future.cause());
            }
        });
    }
}