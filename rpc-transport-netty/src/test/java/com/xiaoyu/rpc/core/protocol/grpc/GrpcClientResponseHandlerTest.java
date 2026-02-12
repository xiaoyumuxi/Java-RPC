package com.xiaoyu.rpc.core.protocol.grpc;

import com.google.protobuf.ByteString;
import com.xiaoyu.rpc.common.vo.RpcResponse;
import com.xiaoyu.rpc.core.client.NettyRpcClientHandler;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http2.DefaultHttp2DataFrame;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.Http2Headers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("gRPC 客户端响应处理器测试")
class GrpcClientResponseHandlerTest {

    @Test
    @DisplayName("应把 DataFrame 解码为 RpcResponse 并完成 future")
    void testDecodeDataFrameToRpcResponse() throws Exception {
        NettyRpcClientHandler clientHandler = new NettyRpcClientHandler();
        EmbeddedChannel channel = new EmbeddedChannel(
                new GrpcClientResponseHandler(clientHandler, "req-1"),
                clientHandler);

        CompletableFuture<Object> future = new CompletableFuture<>();
        clientHandler.addFuture("req-1", future);

        RpcResponse response = RpcResponse.newBuilder()
                .setRequestId("req-1")
                .setMessage("Success")
                .setData(ByteString.copyFromUtf8("ok"))
                .build();

        byte[] payload = response.toByteArray();
        ByteBuf buf = Unpooled.buffer();
        buf.writeByte(0);
        buf.writeInt(payload.length);
        buf.writeBytes(payload);

        channel.writeInbound(new DefaultHttp2DataFrame(buf, true));

        assertTrue(future.isDone(), "Future should be completed");
        Object result = future.get();
        assertInstanceOf(RpcResponse.class, result);
        assertEquals("req-1", ((RpcResponse) result).getRequestId());
        assertEquals("Success", ((RpcResponse) result).getMessage());
    }

    @Test
    @DisplayName("收到 grpc-status 非 0 时应异常完成 future")
    void testFailFutureOnGrpcErrorStatus() {
        NettyRpcClientHandler clientHandler = new NettyRpcClientHandler();
        EmbeddedChannel channel = new EmbeddedChannel(
                new GrpcClientResponseHandler(clientHandler, "req-2"),
                clientHandler);

        CompletableFuture<Object> future = new CompletableFuture<>();
        clientHandler.addFuture("req-2", future);

        Http2Headers trailers = new io.netty.handler.codec.http2.DefaultHttp2Headers()
                .set("grpc-status", "13")
                .set("grpc-message", "internal");
        channel.writeInbound(new DefaultHttp2HeadersFrame(trailers, true));

        assertTrue(future.isCompletedExceptionally(), "Future should be completed exceptionally");
        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        String message = ex.getCause().getMessage();
        assertNotNull(message, "Error message should not be null");
        assertTrue(message.toLowerCase().contains("grpc"), "Error message should include grpc details");
    }
}
