package com.xiaoyu.rpc.core.client;

import com.xiaoyu.rpc.common.vo.RpcResponse;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelHandler;

/**
 * 客户端响应处理器。
 * 通过 requestId 将响应路由回对应的 CompletableFuture。
 */
@ChannelHandler.Sharable
public class NettyRpcClientHandler extends SimpleChannelInboundHandler<RpcResponse> {
    private static final Logger log = LoggerFactory.getLogger(NettyRpcClientHandler.class);

    // 一个连接上可以并发多个请求，靠 requestId 区分各自回调
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
        // 连接级异常通常影响当前连接上的全部在途请求，统一失败返回给上层
        for (CompletableFuture<Object> future : pendingRequests.values()) {
            future.completeExceptionally(cause);
        }
        pendingRequests.clear();
        ctx.close();
    }
}
