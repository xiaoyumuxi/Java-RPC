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
        // 读取 Body 数据
        ByteBuf content = msg.content();
        byte[] bytes = new byte[content.readableBytes()];
        content.readBytes(bytes);

        // 反序列化
        Object obj = serializer.deserialize(bytes, genericClass);

        // 如果是 Request，可以从 Header 中校验方法名（可选）
        if (msg instanceof FullHttpRequest) {
            String methodName = ((FullHttpRequest) msg).headers().get("Rpc-Method");
            // log.info("当前解码获取到的的methodName：{}",methodName);
        }

        out.add(obj);
    }
}