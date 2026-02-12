package com.xiaoyu.rpc.core.protocol.http;

import com.xiaoyu.rpc.common.serialization.Serializer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.http.FullHttpMessage;
import io.netty.handler.codec.http.FullHttpRequest;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class HttpRpcDecoder extends MessageToMessageDecoder<FullHttpMessage> {
    private final Serializer serializer;
    private final Class<?> genericClass;

    public HttpRpcDecoder(Serializer serializer, Class<?> genericClass) {
        this.serializer = serializer;
        this.genericClass = genericClass;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, FullHttpMessage msg, List<Object> out) {
        // FullHttpMessage 已经由聚合器拼成完整报文，这里可以一次性读取 body
        ByteBuf content = msg.content();
        byte[] bytes = new byte[content.readableBytes()];
        content.readBytes(bytes);

        // 直接按目标类型反序列化为 RpcRequest / RpcResponse
        Object obj = serializer.deserialize(bytes, genericClass);

        // 方法名放在 Header 里，主要用于排查和链路观察，不参与核心反序列化流程
        if (msg instanceof FullHttpRequest) {
            String methodName = ((FullHttpRequest) msg).headers().get("Rpc-Method");
            // log.info("当前解码获取到的的methodName：{}",methodName);
        }

        out.add(obj);
    }
}
