package com.xiaoyu.rpc.core.protocol.http;

import com.xiaoyu.rpc.common.serialization.Serializer;
import com.xiaoyu.rpc.common.vo.RpcRequest;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.http.*;
import lombok.extern.slf4j.Slf4j;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class HttpRpcEncoder extends MessageToMessageEncoder<Object> {
    private static final Logger log = LoggerFactory.getLogger(HttpRpcEncoder.class);
    private final Serializer serializer;

    public HttpRpcEncoder(Serializer serializer) {
        this.serializer = serializer;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, List<Object> out) {
        // RPC 对象先序列化成二进制，再包成 HTTP 消息体
        byte[] body = serializer.serialize(msg);
        FullHttpMessage httpMessage;

        if (msg instanceof RpcRequest) {
            RpcRequest request = (RpcRequest) msg;
            // 创建 POST 请求
            FullHttpRequest httpRequest = new DefaultFullHttpRequest(
                    HttpVersion.HTTP_1_1,
                    HttpMethod.POST,
                    "/",
                    Unpooled.wrappedBuffer(body));
            // 方法名放 Header，便于服务端排查请求来源
            httpRequest.headers().set("Rpc-Method", request.getMethodName());
            // log.info("使用 HTTP 协议发送请求，方法名: {}，正在Encode", request.getMethodName());
            httpRequest.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/x-rpc");
            httpMessage = httpRequest;
        } else {
            // 创建 200 OK 响应
            httpMessage = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1,
                    HttpResponseStatus.OK,
                    Unpooled.wrappedBuffer(body));
        }

        // 显式设置长度，避免对端按分块模式误判读取边界
        httpMessage.headers().set(HttpHeaderNames.CONTENT_LENGTH, body.length);
        out.add(httpMessage);
    }
}
