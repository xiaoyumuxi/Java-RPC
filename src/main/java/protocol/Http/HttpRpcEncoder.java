package protocol.Http;

import Serialization.Serializer;
import VO.RpcRequest;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.http.*;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
@Slf4j
public class HttpRpcEncoder extends MessageToMessageEncoder<Object> {
    private final Serializer serializer;

    public HttpRpcEncoder(Serializer serializer) {
        this.serializer = serializer;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, List<Object> out) {
        byte[] body = serializer.serialize(msg);
        FullHttpMessage httpMessage;

        if (msg instanceof RpcRequest) {
            RpcRequest request = (RpcRequest) msg;
            // 创建 POST 请求
            FullHttpRequest httpRequest = new DefaultFullHttpRequest(
                    HttpVersion.HTTP_1_1,
                    HttpMethod.POST,
                    "/",
                    Unpooled.wrappedBuffer(body)
            );
            // 将方法名放入 Header
            httpRequest.headers().set("Rpc-Method", request.getMethodName());
            log.info("使用 HTTP 协议发送请求，方法名: {}，正在Encode", request.getMethodName());
            httpRequest.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/x-rpc");
            httpMessage = httpRequest;
        } else {
            // 创建 200 OK 响应
            httpMessage = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1,
                    HttpResponseStatus.OK,
                    Unpooled.wrappedBuffer(body)
            );
        }

        // 设置必要的 HTTP 长度头
        httpMessage.headers().set(HttpHeaderNames.CONTENT_LENGTH, body.length);
        out.add(httpMessage);
    }
}