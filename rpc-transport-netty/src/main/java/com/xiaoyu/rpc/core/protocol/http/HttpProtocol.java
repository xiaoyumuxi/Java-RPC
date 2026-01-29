package com.xiaoyu.rpc.core.protocol.http;

import com.xiaoyu.rpc.common.serialization.Serializer;
import com.xiaoyu.rpc.common.serialization.SerializerCode;
import com.xiaoyu.rpc.common.vo.RpcRequest;
import com.xiaoyu.rpc.common.vo.RpcResponse;
import com.xiaoyu.rpc.core.config.RpcConfig;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpClientCodec;
import com.xiaoyu.rpc.core.protocol.Protocol;

public class HttpProtocol implements Protocol {

    @Override
    public String getName() {
        return "http";
    }

    @Override
    public void config(ChannelPipeline pipeline, boolean isServer, io.netty.channel.ChannelHandler serverHandler) {
        // 1. 获取序列化器
        RpcConfig rpcConfig = RpcConfig.getInstance();
        Serializer serializer = SerializerCode.getSerializerByCode(rpcConfig.getSerializerCode());

        // 2. HTTP 编解码基础
        if (isServer) {
            pipeline.addLast(new HttpServerCodec());
        } else {
            pipeline.addLast(new HttpClientCodec());
        }
        pipeline.addLast(new HttpObjectAggregator(512 * 1024));

        // 3. HTTP 与 RpcObject 的转换层
        if (isServer) {
            // 服务端：解码 Request，编码 Response
            pipeline.addLast(new HttpRpcDecoder(serializer, RpcRequest.class));
            pipeline.addLast(new HttpRpcEncoder(serializer));
        } else {
            // 客户端：编码 Request，解码 Response
            pipeline.addLast(new HttpRpcEncoder(serializer));
            pipeline.addLast(new HttpRpcDecoder(serializer, RpcResponse.class));
        }

        if (isServer && serverHandler != null) {
            pipeline.addLast(serverHandler);
        }
    }

    @Override
    public void sendRequest(io.netty.channel.Channel channel, RpcRequest request,
            com.xiaoyu.rpc.core.client.NettyRpcClientHandler clientHandler) throws Exception {
        // HTTP 1.1 协议也复用主通道
        if (channel.pipeline().get(com.xiaoyu.rpc.core.client.NettyRpcClientHandler.class) != null) {
            channel.pipeline().remove(com.xiaoyu.rpc.core.client.NettyRpcClientHandler.class);
        }
        channel.pipeline().addLast(clientHandler);

        channel.writeAndFlush(request).addListener(future -> {
            if (!future.isSuccess()) {
                clientHandler.getFuture().completeExceptionally(future.cause());
            }
        });
    }
}