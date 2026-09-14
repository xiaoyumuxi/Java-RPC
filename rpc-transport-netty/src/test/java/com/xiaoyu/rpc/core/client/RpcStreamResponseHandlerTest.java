package com.xiaoyu.rpc.core.client;

import com.xiaoyu.rpc.common.vo.RpcResponse;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http2.DefaultHttp2ResetFrame;
import io.netty.handler.codec.http2.Http2Error;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;

class RpcStreamResponseHandlerTest {
    @Test
    void completedStreamDoesNotFailSiblingOrNextRequest() {
        NettyRpcClientHandler shared = new NettyRpcClientHandler();
        EmbeddedChannel parent = new EmbeddedChannel(shared);
        EmbeddedChannel first = new EmbeddedChannel(new RpcStreamResponseHandler(shared, "a"));
        EmbeddedChannel second = new EmbeddedChannel(new RpcStreamResponseHandler(shared, "b"));
        try {
            CompletableFuture<Object> a = new CompletableFuture<>();
            CompletableFuture<Object> b = new CompletableFuture<>();
            shared.addFuture("a", a);
            shared.addFuture("b", b);
            first.writeInbound(RpcResponse.newBuilder().setRequestId("a").build());
            first.close();
            assertFalse(a.isCompletedExceptionally());
            assertTrue(a.isDone());
            assertFalse(b.isDone());
            second.writeInbound(RpcResponse.newBuilder().setRequestId("b").build());
            assertTrue(b.isDone());
            assertFalse(b.isCompletedExceptionally());
            assertEquals(0, shared.pendingRequestCount());
        } finally {
            first.finishAndReleaseAll();
            second.finishAndReleaseAll();
            parent.finishAndReleaseAll();
        }
    }

    @Test
    void resetFailsOnlyItsStreamAndParentCloseStillFailsAll() {
        NettyRpcClientHandler shared = new NettyRpcClientHandler();
        EmbeddedChannel parent = new EmbeddedChannel(shared);
        EmbeddedChannel child = new EmbeddedChannel(new RpcStreamResponseHandler(shared, "a"));
        try {
            CompletableFuture<Object> a = new CompletableFuture<>();
            CompletableFuture<Object> b = new CompletableFuture<>();
            shared.addFuture("a", a);
            shared.addFuture("b", b);
            child.pipeline().fireUserEventTriggered(new DefaultHttp2ResetFrame(Http2Error.CANCEL));
            assertTrue(a.isCompletedExceptionally());
            assertFalse(b.isDone());
            parent.close();
            assertTrue(b.isCompletedExceptionally());
            assertEquals(0, shared.pendingRequestCount());
        } finally {
            child.finishAndReleaseAll();
            parent.finishAndReleaseAll();
        }
    }

    @Test
    void mismatchedResponseCannotCompleteAnotherStream() {
        NettyRpcClientHandler shared = new NettyRpcClientHandler();
        EmbeddedChannel channel = new EmbeddedChannel(new RpcStreamResponseHandler(shared, "a"));
        try {
            CompletableFuture<Object> a = new CompletableFuture<>();
            CompletableFuture<Object> b = new CompletableFuture<>();
            shared.addFuture("a", a);
            shared.addFuture("b", b);
            channel.writeInbound(RpcResponse.newBuilder().setRequestId("b").build());
            assertTrue(a.isCompletedExceptionally());
            assertFalse(b.isDone());
        } finally {
            channel.finishAndReleaseAll();
            shared.failAll(new IllegalStateException("test cleanup"));
        }
    }
}
