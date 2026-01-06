package com.xiaoyu.rpc.core.protocol.netty;

import com.xiaoyu.rpc.common.serialization.Serializer;
import com.xiaoyu.rpc.common.serialization.SerializerCode;
import com.xiaoyu.rpc.common.vo.RpcRequest;
import com.xiaoyu.rpc.common.vo.RpcResponse;
import com.xiaoyu.rpc.core.config.RpcConfig;
import io.netty.channel.ChannelPipeline;
import com.xiaoyu.rpc.core.protocol.Protocol;

public class NettyProtocol implements Protocol {

    @Override
    public String getName() {
        return "netty";
    }

    @Override
    public void config(ChannelPipeline pipeline, boolean isServer, io.netty.channel.ChannelHandler serverHandler) {
        // 1. 获取配置
        RpcConfig rpcConfig = RpcConfig.getInstance();
        byte code = rpcConfig.getSerializerCode();
        Serializer serializer = SerializerCode.getSerializerByCode(code);

        // 2. 判断解码类型
        // 如果是服务端(isServer=true)，我要读 Request
        // 如果是客户端(isServer=false)，我要读 Response
        if (isServer) {
            pipeline.addLast(new MyRpcDecoder(RpcRequest.class));
        } else {
            pipeline.addLast(new MyRpcDecoder(RpcResponse.class));
        }

        // 3. 编码器 (收发都需要编码)
        pipeline.addLast(new MyRpcEncoder(serializer));

        if (isServer && serverHandler != null) {
            pipeline.addLast(serverHandler);
        }
    }

    @Override
    public void sendRequest(io.netty.channel.Channel channel, RpcRequest request,
            com.xiaoyu.rpc.core.client.NettyRpcClientHandler clientHandler) throws Exception {
        // Netty 协议直接复用主通道
        // 如果 pipeline 里还没有 handler (第一次)，加上它
        if (channel.pipeline().get(com.xiaoyu.rpc.core.client.NettyRpcClientHandler.class) != null) {
            channel.pipeline().replace(com.xiaoyu.rpc.core.client.NettyRpcClientHandler.class, "handler", clientHandler);
        } else {
            channel.pipeline().addLast("handler", clientHandler);
        }

        channel.writeAndFlush(request).addListener(future -> {
            if (!future.isSuccess()) {
                clientHandler.getFuture().completeExceptionally(future.cause());
            }
        });
    }
}