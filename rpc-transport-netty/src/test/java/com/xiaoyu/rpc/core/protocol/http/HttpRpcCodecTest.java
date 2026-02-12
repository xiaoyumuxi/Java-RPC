package com.xiaoyu.rpc.core.protocol.http;

import com.google.protobuf.ByteString;
import com.xiaoyu.rpc.common.extension.ExtensionLoader;
import com.xiaoyu.rpc.common.serialization.Serializer;
import com.xiaoyu.rpc.common.vo.RpcRequest;
import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("HTTP 编解码边界测试")
public class HttpRpcCodecTest {

    @Test
    @DisplayName("RpcRequest 编码为 HTTP POST 报文")
    void testEncodeRequest() {
        Serializer serializer = ExtensionLoader.getExtensionLoader(Serializer.class).getExtension("java");
        EmbeddedChannel channel = new EmbeddedChannel(new HttpRpcEncoder(serializer));

        RpcRequest request = RpcRequest.newBuilder()
                .setInterfaceName("com.example.DemoService")
                .setMethodName("hello")
                .addParamTypes("java.lang.String")
                .addParameters(ByteString.copyFrom(serializer.serialize("world")))
                .build();

        assertTrue(channel.writeOutbound(request));
        Object outbound = channel.readOutbound();
        assertInstanceOf(FullHttpRequest.class, outbound);

        FullHttpRequest httpRequest = (FullHttpRequest) outbound;
        assertEquals(HttpMethod.POST, httpRequest.method());
        assertEquals("/", httpRequest.uri());
        assertEquals("hello", httpRequest.headers().get("Rpc-Method"));
        assertEquals("application/x-rpc", httpRequest.headers().get("Content-Type"));

        byte[] body = bytes(httpRequest.content());
        RpcRequest decoded = serializer.deserialize(body, RpcRequest.class);
        assertEquals("hello", decoded.getMethodName());
    }

    @Test
    @DisplayName("HTTP 响应解码为目标对象")
    void testDecodeResponse() {
        Serializer serializer = ExtensionLoader.getExtensionLoader(Serializer.class).getExtension("java");
        EmbeddedChannel channel = new EmbeddedChannel(new HttpRpcDecoder(serializer, String.class));

        byte[] body = serializer.serialize("pong");
        FullHttpResponse response = new io.netty.handler.codec.http.DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.OK,
                io.netty.buffer.Unpooled.wrappedBuffer(body));

        assertTrue(channel.writeInbound(response));
        Object inbound = channel.readInbound();
        assertEquals("pong", inbound);
    }

    private static byte[] bytes(ByteBuf buf) {
        byte[] bytes = new byte[buf.readableBytes()];
        buf.getBytes(buf.readerIndex(), bytes);
        return bytes;
    }
}
