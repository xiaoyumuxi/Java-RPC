package com.xiaoyu.rpc.core.protocol.grpc;

import com.google.protobuf.ByteString;
import com.xiaoyu.rpc.common.vo.RpcRequest;
import com.xiaoyu.rpc.common.vo.RpcResponse;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http2.DefaultHttp2DataFrame;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("gRPC 服务端处理器帧测试")
public class GrpcServerHandlerTest {

    @Test
    @DisplayName("数据帧不足 5 字节前缀时忽略")
    void testIgnoreShortDataFrame() {
        EmbeddedChannel channel = new EmbeddedChannel(new GrpcServerHandler(new ChannelInboundHandlerAdapter()));
        Http2DataFrame frame = new DefaultHttp2DataFrame(Unpooled.wrappedBuffer(new byte[] { 1, 2, 3, 4 }), true);

        channel.writeInbound(frame);
        assertNull(channel.readInbound(), "Short frame should not produce RpcRequest");
    }

    @Test
    @DisplayName("完整 gRPC 数据帧可还原 RpcRequest")
    void testDecodeGrpcRequestFrame() {
        EmbeddedChannel channel = new EmbeddedChannel(new GrpcServerHandler(new ChannelInboundHandlerAdapter()));
        RpcRequest request = RpcRequest.newBuilder()
                .setInterfaceName("com.example.DemoService")
                .setMethodName("hello")
                .build();
        byte[] payload = request.toByteArray();

        ByteBuf buf = Unpooled.buffer();
        buf.writeByte(0);
        buf.writeInt(payload.length);
        buf.writeBytes(payload);

        channel.writeInbound(new DefaultHttp2DataFrame(buf, true));
        Object inbound = channel.readInbound();

        assertInstanceOf(RpcRequest.class, inbound);
        assertEquals("hello", ((RpcRequest) inbound).getMethodName());
    }

    @Test
    @DisplayName("RpcResponse 写出时应包含 headers/data/trailers")
    void testWriteRpcResponseAsGrpcFrames() {
        EmbeddedChannel channel = new EmbeddedChannel(new GrpcServerHandler(new ChannelInboundHandlerAdapter()));
        RpcResponse response = RpcResponse.newBuilder()
                .setRequestId("req-1")
                .setMessage("Success")
                .setData(ByteString.copyFromUtf8("ok"))
                .build();

        assertTrue(channel.writeOutbound(response));

        Object first = channel.readOutbound();
        Object second = channel.readOutbound();
        Object third = channel.readOutbound();

        assertInstanceOf(Http2HeadersFrame.class, first);
        assertInstanceOf(Http2DataFrame.class, second);
        assertInstanceOf(Http2HeadersFrame.class, third);

        Http2Headers headers = ((Http2HeadersFrame) first).headers();
        assertEquals("200", headers.status().toString());
        assertEquals("application/grpc", headers.get("content-type").toString());

        ByteBuf data = ((Http2DataFrame) second).content();
        assertEquals(0, data.readByte(), "gRPC compressed flag should be 0");
        int len = data.readInt();
        assertTrue(len > 0, "Payload length should be positive");

        Http2Headers trailers = ((Http2HeadersFrame) third).headers();
        assertEquals("0", trailers.get("grpc-status").toString());
        assertTrue(((Http2HeadersFrame) third).isEndStream(), "Trailer frame should end stream");
    }
}
