package com.xiaoyu.rpc.core.protocol.grpc;

import com.xiaoyu.rpc.common.vo.RpcRequest;
import com.xiaoyu.rpc.core.client.NettyRpcClientHandler;
import com.xiaoyu.rpc.core.protocol.Protocol;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http2.DefaultHttp2DataFrame;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.Http2StreamChannel;
import io.netty.handler.codec.http2.Http2StreamChannelBootstrap;
import io.netty.handler.codec.http2.Http2MultiplexHandler;
import io.netty.util.ReferenceCountUtil;

import java.net.InetSocketAddress;

public class GrpcProtocol implements Protocol {

    @Override
    public String getName() {
        return "grpc";
    }

    @Override
    public void config(ChannelPipeline pipeline, boolean isServer, ChannelHandler serverHandler) {
        if (isServer) {
            // 先接入 HTTP/2 帧编解码，处理握手并产出 Frame
            pipeline.addLast(Http2FrameCodecBuilder.forServer().build());

            // 再通过 MultiplexHandler 为每个 Stream 创建子 Channel
            pipeline.addLast(new Http2MultiplexHandler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) throws Exception {
                    ChannelPipeline p = ch.pipeline();
                    // 在子 Channel 中添加 gRPC 适配器
                    p.addLast(new GrpcServerHandler(serverHandler));
                    // 添加业务处理器 (复用现有的 NettyRpcHandler)
                    p.addLast(serverHandler);
                }
            }));
        } else {
            pipeline.addLast(Http2FrameCodecBuilder.forClient()
                    .autoAckSettingsFrame(true)
                    .autoAckPingFrame(true)
                    .initialSettings(Http2Settings.defaultSettings().maxHeaderListSize(8192))
                    .build());
            pipeline.addLast(new Http2MultiplexHandler(new ChannelInboundHandlerAdapter() {
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) {
                    // 连接级残留帧统一释放，避免引用计数对象泄漏
                    ReferenceCountUtil.release(msg);
                }
            }));
        }
    }

    @Override
    public void sendRequest(Channel channel, RpcRequest request, NettyRpcClientHandler clientHandler) throws Exception {
        Http2StreamChannelBootstrap streamBootstrap = new Http2StreamChannelBootstrap(channel);
        streamBootstrap.open().addListener(openFuture -> {
            if (!openFuture.isSuccess()) {
                clientHandler.failRequest(request.getRequestId(), openFuture.cause());
                return;
            }

            Http2StreamChannel streamChannel = (Http2StreamChannel) openFuture.getNow();
            streamChannel.pipeline().addLast(new GrpcClientResponseHandler(clientHandler, request.getRequestId()));
            streamChannel.pipeline().addLast(clientHandler);

            byte[] payload = request.toByteArray();
            io.netty.buffer.ByteBuf body = streamChannel.alloc().buffer(payload.length + 5);
            body.writeByte(0); // compressed-flag
            body.writeInt(payload.length);
            body.writeBytes(payload);

            Http2Headers headers = new DefaultHttp2Headers()
                    .method("POST")
                    .path("/GrpcService/handle")
                    .scheme("http")
                    .set(HttpHeaderNames.CONTENT_TYPE, "application/grpc")
                    .set(HttpHeaderNames.TE, "trailers");

            if (channel.remoteAddress() instanceof InetSocketAddress) {
                InetSocketAddress remote = (InetSocketAddress) channel.remoteAddress();
                headers.authority(remote.getHostString() + ":" + remote.getPort());
            }

            streamChannel.write(new DefaultHttp2HeadersFrame(headers, false));
            streamChannel.writeAndFlush(new DefaultHttp2DataFrame(body, true)).addListener(writeFuture -> {
                if (!writeFuture.isSuccess()) {
                    clientHandler.failRequest(request.getRequestId(), writeFuture.cause());
                }
            });
        });
    }
}
