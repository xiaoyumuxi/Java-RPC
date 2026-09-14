package com.xiaoyu.rpc.core.protocol.http2;

import com.xiaoyu.rpc.common.serialization.Serializer;
import com.xiaoyu.rpc.common.serialization.SerializerCode;
import com.xiaoyu.rpc.common.vo.RpcRequest;
import com.xiaoyu.rpc.common.vo.RpcResponse;
import com.xiaoyu.rpc.core.config.RpcConfig;
import com.xiaoyu.rpc.core.protocol.Protocol;
import com.xiaoyu.rpc.core.protocol.http.HttpRpcDecoder;
import com.xiaoyu.rpc.core.protocol.http.HttpRpcEncoder;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http2.Http2FrameCodec;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2MultiplexHandler;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.Http2StreamChannel;
import io.netty.handler.codec.http2.Http2StreamChannelBootstrap;
import io.netty.handler.codec.http2.Http2StreamFrameToHttpObjectCodec;
import io.netty.util.ReferenceCountUtil;

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
                    .autoAckSettingsFrame(true)
                    .autoAckPingFrame(true)
                    .build();

            Http2MultiplexHandler multiplexHandler = new Http2MultiplexHandler(
                    new ChannelInitializer<Http2StreamChannel>() {
                        @Override
                        protected void initChannel(Http2StreamChannel ch) {
                            ch.pipeline().addLast(new Http2StreamFrameToHttpObjectCodec(true));
                            ch.pipeline().addLast(new HttpObjectAggregator(512 * 1024));
                            ch.pipeline().addLast(new HttpRpcDecoder(serializer, RpcRequest.class));
                            ch.pipeline().addLast(new HttpRpcEncoder(serializer));

                            if (serverHandler != null) {
                                ch.pipeline().addLast(serverHandler);
                            }
                        }
                    });
            pipeline.addLast(frameCodec, multiplexHandler);
        } else {
            Http2FrameCodec frameCodec = Http2FrameCodecBuilder.forClient()
                    .autoAckSettingsFrame(true)
                    .autoAckPingFrame(true)
                    .initialSettings(Http2Settings.defaultSettings().maxHeaderListSize(8192))
                    .build();

            pipeline.addLast(frameCodec);
            // Do not create/write a stream until Netty confirms that the HTTP/2 client preface and SETTINGS are sent.
            Http2ClientConnectionReadyHandler.install(pipeline);
            pipeline.addLast(new Http2MultiplexHandler(new ChannelInboundHandlerAdapter() {
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) {
                    ReferenceCountUtil.release(msg);
                }

                @Override
                public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                    ctx.fireUserEventTriggered(evt);
                }
            }));
        }
    }

    @Override
    public void sendRequest(Channel channel, RpcRequest request,
            com.xiaoyu.rpc.core.client.NettyRpcClientHandler clientHandler) {
        RpcConfig rpcConfig = RpcConfig.getInstance();
        Serializer serializer = SerializerCode.getSerializerByCode(rpcConfig.getSerializerCode());

        Http2ClientConnectionReadyHandler.readinessFuture(channel).whenComplete((ignored, readinessError) -> {
            if (readinessError != null) {
                clientHandler.failRequest(request.getRequestId(), readinessError);
                return;
            }
            openStreamAndSend(channel, request, serializer, clientHandler);
        });
    }

    private void openStreamAndSend(Channel channel, RpcRequest request, Serializer serializer,
            com.xiaoyu.rpc.core.client.NettyRpcClientHandler clientHandler) {
        Http2StreamChannelBootstrap streamBootstrap = new Http2StreamChannelBootstrap(channel);
        streamBootstrap.open().addListener(f -> {
            if (!f.isSuccess()) {
                clientHandler.failRequest(request.getRequestId(), f.cause());
                return;
            }

            Http2StreamChannel streamChannel = (Http2StreamChannel) f.getNow();
            streamChannel.pipeline().addLast(new Http2StreamFrameToHttpObjectCodec(false));
            streamChannel.pipeline().addLast(new HttpObjectAggregator(512 * 1024));
            streamChannel.pipeline().addLast(new HttpRpcEncoder(serializer));
            streamChannel.pipeline().addLast(new HttpRpcDecoder(serializer, RpcResponse.class));
            streamChannel.pipeline().addLast(clientHandler);

            streamChannel.writeAndFlush(request).addListener(writeFuture -> {
                if (!writeFuture.isSuccess()) {
                    clientHandler.failRequest(request.getRequestId(), writeFuture.cause());
                }
            });
        });
    }
}
