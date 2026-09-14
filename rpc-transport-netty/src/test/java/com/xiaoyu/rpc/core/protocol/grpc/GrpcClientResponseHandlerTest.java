package com.xiaoyu.rpc.core.protocol.grpc;

import com.google.protobuf.ByteString;
import com.xiaoyu.rpc.common.vo.RpcResponse;
import com.xiaoyu.rpc.core.client.NettyRpcClientHandler;
import com.xiaoyu.rpc.core.client.RpcStreamResponseHandler;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.handler.codec.http2.DefaultHttp2DataFrame;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("gRPC 客户端响应处理器测试")
class GrpcClientResponseHandlerTest {

    @Test
    @DisplayName("完整消息加成功状态尾帧才完成 future")
    void testDecodeDataFrameToRpcResponse() throws Exception {
        NettyRpcClientHandler clientHandler = new NettyRpcClientHandler();
        EmbeddedChannel channel = channel(clientHandler, "req-1");
        CompletableFuture<Object> future = new CompletableFuture<>();
        clientHandler.addFuture("req-1", future);
        RpcResponse response = response("req-1");
        try {
            channel.writeInbound(new DefaultHttp2HeadersFrame(new DefaultHttp2Headers()
                    .status("200").set("content-type", "application/grpc"), false));
            channel.writeInbound(new DefaultHttp2DataFrame(framed(response), false));
            assertFalse(future.isDone(), "A DATA frame does not establish gRPC success");
            channel.writeInbound(new DefaultHttp2HeadersFrame(new DefaultHttp2Headers()
                    .set("grpc-status", "0"), true));
            assertEquals(response, future.get(1, TimeUnit.SECONDS));
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    @DisplayName("收到 grpc-status 非 0 时应异常完成 future")
    void testFailFutureOnGrpcErrorStatus() {
        NettyRpcClientHandler clientHandler = new NettyRpcClientHandler();
        EmbeddedChannel channel = channel(clientHandler, "req-2");
        CompletableFuture<Object> future = new CompletableFuture<>();
        clientHandler.addFuture("req-2", future);
        try {
            channel.writeInbound(new DefaultHttp2HeadersFrame(new DefaultHttp2Headers()
                    .status("200").set("grpc-status", "13").set("grpc-message", "internal"), true));
            assertTrue(future.isCompletedExceptionally());
            ExecutionException error = assertThrows(ExecutionException.class,
                    () -> future.get(1, TimeUnit.SECONDS));
            assertTrue(error.getCause().getMessage().contains("gRPC status=13"));
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    @DisplayName("DATA 直接结束且缺少状态尾帧时必须失败")
    void testMissingTrailersCannotBeSuccess() {
        NettyRpcClientHandler clientHandler = new NettyRpcClientHandler();
        EmbeddedChannel channel = channel(clientHandler, "req-3");
        CompletableFuture<Object> future = new CompletableFuture<>();
        clientHandler.addFuture("req-3", future);
        try {
            channel.writeInbound(new DefaultHttp2DataFrame(framed(response("req-3")), true));
            ExecutionException error = assertThrows(ExecutionException.class,
                    () -> future.get(1, TimeUnit.SECONDS));
            assertInstanceOf(CorruptedFrameException.class, error.getCause());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    private static EmbeddedChannel channel(NettyRpcClientHandler handler, String requestId) {
        return new EmbeddedChannel(new GrpcClientResponseHandler(handler, requestId),
                new RpcStreamResponseHandler(handler, requestId));
    }

    private static RpcResponse response(String requestId) {
        return RpcResponse.newBuilder().setRequestId(requestId).setMessage("Success")
                .setData(ByteString.copyFromUtf8("ok")).build();
    }

    private static ByteBuf framed(RpcResponse response) {
        byte[] payload = response.toByteArray();
        return Unpooled.buffer(payload.length + 5).writeByte(0).writeInt(payload.length).writeBytes(payload);
    }
}
