package com.xiaoyu.rpc.core.client;

import com.xiaoyu.rpc.common.vo.RpcResponse;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;

// 这是一个 Netty 的 Handler，专门负责“收信”
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelHandler;

// 这是一个 Netty 的 Handler，专门负责“收信”
@ChannelHandler.Sharable
public class NettyRpcClientHandler extends SimpleChannelInboundHandler<RpcResponse> {
    private static final Logger log = LoggerFactory.getLogger(NettyRpcClientHandler.class);

    // Key: RequestId, Value: Future
    private final java.util.Map<String, CompletableFuture<Object>> pendingRequests = new java.util.concurrent.ConcurrentHashMap<>();

    public void addFuture(String requestId, CompletableFuture<Object> future) {
        pendingRequests.put(requestId, future);
    }

    public void removeFuture(String requestId) {
        pendingRequests.remove(requestId);
    }

    public void failRequest(String requestId, Throwable cause) {
        CompletableFuture<Object> future = pendingRequests.remove(requestId);
        if (future != null) {
            future.completeExceptionally(cause);
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcResponse response) {
        String requestId = response.getRequestId();
        CompletableFuture<Object> future = pendingRequests.remove(requestId);

        if (future != null) {
            log.info("Client received response for requestId: {}, status: {}", requestId, response.getMessage());
            future.complete(response);
        } else {
            log.warn("Client received response for unknown or timed-out requestId: {}", requestId);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Client caught exception", cause);
        // Fail all pending requests
        for (CompletableFuture<Object> future : pendingRequests.values()) {
            future.completeExceptionally(cause);
        }
        pendingRequests.clear();
        ctx.close();
    }

    // public CompletableFuture<Object> getFuture() { ... } // Removed single getter
}