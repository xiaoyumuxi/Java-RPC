package protocol.Http;

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
import protocol.Protocol;

public class Http2Protocol implements Protocol {

    @Override
    public String getName() {
        return "http2";
    }

    @Override
    public void config(ChannelPipeline pipeline, boolean isServer) {
        RpcConfig rpcConfig = RpcConfig.getInstance();
        Serializer serializer = SerializerCode.getSerializerByCode(rpcConfig.getSerializerCode());

        if (isServer) {
            Http2FrameCodec frameCodec = Http2FrameCodecBuilder.forServer().build();
            // 使用新的构造方式，指定子通道的处理器
            Http2MultiplexHandler multiplexHandler = new Http2MultiplexHandler(new ChannelInitializer<Http2StreamChannel>() {
                @Override
                protected void initChannel(Http2StreamChannel ch) {
                    // 在子通道（Stream）中注入转换层
                    ch.pipeline().addLast(new Http2StreamFrameToHttpObjectCodec(true));
                    ch.pipeline().addLast(new HttpRpcDecoder(serializer, RpcRequest.class));
                    ch.pipeline().addLast(new HttpRpcEncoder(serializer));
                    // 注意：RpcServer.java 里的 initChannel 会在这之后 addLast(NettyRpcHandler)
                }
            });
            pipeline.addLast(frameCodec, multiplexHandler);

        } else {
            // 客户端核心配置
            Http2FrameCodec frameCodec = Http2FrameCodecBuilder.forClient()
                    // 强制自动处理一些基础帧，防止它们掉到 TailContext
                    .autoAckSettingsFrame(true)
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
            }));
        }
    }
}