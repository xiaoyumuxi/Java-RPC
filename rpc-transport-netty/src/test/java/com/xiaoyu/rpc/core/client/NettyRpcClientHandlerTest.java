package com.xiaoyu.rpc.core.client;

import com.google.protobuf.ByteString;
import com.xiaoyu.rpc.common.vo.RpcResponse;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("NettyRpcClientHandler 并发回调测试")
public class NettyRpcClientHandlerTest {

    @Test
    @DisplayName("按 requestId 路由并支持乱序响应")
    void testRouteResponseByRequestId() throws Exception {
        NettyRpcClientHandler handler = new NettyRpcClientHandler();
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        CompletableFuture<Object> f1 = new CompletableFuture<>();
        CompletableFuture<Object> f2 = new CompletableFuture<>();
        handler.addFuture("req-1", f1);
        handler.addFuture("req-2", f2);

        channel.writeInbound(response("req-2", "ok-2"));
        channel.writeInbound(response("req-1", "ok-1"));

        RpcResponse r2 = (RpcResponse) f2.get(1, TimeUnit.SECONDS);
        RpcResponse r1 = (RpcResponse) f1.get(1, TimeUnit.SECONDS);
        assertEquals("ok-2", r2.getMessage());
        assertEquals("ok-1", r1.getMessage());
    }

    @Test
    @DisplayName("未知 requestId 的响应不会污染已挂起请求")
    void testUnknownRequestId() {
        NettyRpcClientHandler handler = new NettyRpcClientHandler();
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        CompletableFuture<Object> future = new CompletableFuture<>();
        handler.addFuture("known", future);

        channel.writeInbound(response("unknown", "ignored"));
        assertFalse(future.isDone(), "Unknown response should not complete unrelated future");
    }

    @Test
    @DisplayName("连接异常时所有挂起请求应失败并清空")
    void testExceptionCaughtFailsAllPending() {
        NettyRpcClientHandler handler = new NettyRpcClientHandler();
        EmbeddedChannel channel = new EmbeddedChannel(handler);

        CompletableFuture<Object> f1 = new CompletableFuture<>();
        CompletableFuture<Object> f2 = new CompletableFuture<>();
        handler.addFuture("a", f1);
        handler.addFuture("b", f2);

        RuntimeException cause = new RuntimeException("boom");
        channel.pipeline().fireExceptionCaught(cause);

        assertTrue(f1.isCompletedExceptionally());
        assertTrue(f2.isCompletedExceptionally());
        assertFalse(channel.isActive(), "Channel should be closed on exception");

        assertThrows(ExecutionException.class, f1::get);
        assertThrows(ExecutionException.class, f2::get);
    }

    private static RpcResponse response(String requestId, String message) {
        return RpcResponse.newBuilder()
                .setRequestId(requestId)
                .setMessage(message)
                .setData(ByteString.EMPTY)
                .build();
    }
}
