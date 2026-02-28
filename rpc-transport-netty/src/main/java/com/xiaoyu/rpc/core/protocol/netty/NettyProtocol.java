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
        // 读取当前序列化配置
        RpcConfig rpcConfig = RpcConfig.getInstance();
        byte code = rpcConfig.getSerializerCode();
        Serializer serializer = SerializerCode.getSerializerByCode(code);

        // 服务端解码 RpcRequest，客户端解码 RpcResponse
        if (isServer) {
            pipeline.addLast(new NettyRpcDecoder(RpcRequest.class));
        } else {
            pipeline.addLast(new NettyRpcDecoder(RpcResponse.class));
        }

        // 编码器在收发两侧都需要
        pipeline.addLast(new NettyRpcEncoder(serializer));

        if (isServer && serverHandler != null) {
            pipeline.addLast(serverHandler);
        }
    }

    @Override
    public void sendRequest(io.netty.channel.Channel channel, RpcRequest request,
            com.xiaoyu.rpc.core.client.NettyRpcClientHandler clientHandler) throws Exception {
        // Netty 协议直接复用主通道
        if (channel.pipeline().get(com.xiaoyu.rpc.core.client.NettyRpcClientHandler.class) == null) {
            channel.pipeline().addLast(clientHandler);
        }

        channel.writeAndFlush(request).addListener(future -> {
            if (!future.isSuccess()) {
                clientHandler.failRequest(request.getRequestId(), future.cause());
            }
        });
    }
}
