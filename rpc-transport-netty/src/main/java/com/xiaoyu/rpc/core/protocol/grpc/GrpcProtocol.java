package com.xiaoyu.rpc.core.protocol.grpc;

import com.xiaoyu.rpc.common.vo.RpcRequest;
import com.xiaoyu.rpc.core.client.NettyRpcClientHandler;
import com.xiaoyu.rpc.core.protocol.Protocol;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2MultiplexHandler;

public class GrpcProtocol implements Protocol {

    @Override
    public String getName() {
        return "grpc";
    }

    @Override
    public void config(ChannelPipeline pipeline, boolean isServer, ChannelHandler serverHandler) {
        if (isServer) {
            // 1. Http2FrameCodec (处理握手、并转为 Frame 对象)
            pipeline.addLast(Http2FrameCodecBuilder.forServer().build());

            // 2. MultiplexHandler (为每个 Stream 创建子 Channel)
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
            throw new UnsupportedOperationException("Client side grpc not supported yet");
        }
    }

    @Override
    public void sendRequest(Channel channel, RpcRequest request, NettyRpcClientHandler clientHandler) throws Exception {
        throw new UnsupportedOperationException("Client side generic grpc not supported yet");
    }
}
